package com.cc.deploy.service;

import com.cc.deploy.config.DeployProperties;
import com.cc.deploy.entity.DeployRecord;
import com.cc.deploy.entity.Project;
import com.cc.deploy.entity.ServerInfo;
import com.cc.deploy.mapper.DeployRecordMapper;
import com.cc.deploy.util.CommandUtil;
import com.cc.deploy.util.SshUtil;
import com.cc.deploy.util.ZipUtil;
import com.cc.deploy.websocket.LogWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单次部署任务：clone/pull → checkout → build → 定位产物(Vue打zip) → SFTP上传 → 远程执行部署脚本
 */
@Slf4j
public class DeployTask implements Runnable {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Project project;
    private final ServerInfo server;
    private final String serverPassword;
    private final DeployRecord record;
    private final DeployRecordMapper recordMapper;
    private final LogWebSocketHandler logHandler;
    private final DeployProperties props;
    private final Runnable onFinish;

    private final StringBuilder logBuffer = new StringBuilder();

    /** 取消标志 */
    private volatile boolean cancelled = false;
    /** 当前正在执行的本地进程（构建阶段） */
    private final AtomicReference<Process> currentProcess = new AtomicReference<>();
    /** SFTP 上传会话（用于中断） */
    private volatile Thread uploadThread = null;

    public DeployTask(Project project, ServerInfo server, String serverPassword,
                      DeployRecord record, DeployRecordMapper recordMapper,
                      LogWebSocketHandler logHandler, DeployProperties props, Runnable onFinish) {
        this.project = project;
        this.server = server;
        this.serverPassword = serverPassword;
        this.record = record;
        this.recordMapper = recordMapper;
        this.logHandler = logHandler;
        this.props = props;
        this.onFinish = onFinish;
    }

    /**
     * 取消部署
     *
     * @return true=成功取消，false=无法取消（已在远程阶段）
     */
    public boolean cancel() {
        String step = record.getCurrentStep();
        // 远程部署、检查脚本、清理脚本阶段不允许取消
        if ("远程部署".equals(step) || "检查脚本".equals(step)) {
            appendLog("[CANCEL] 当前处于远程操作阶段，无法终止");
            return false;
        }
        cancelled = true;
        appendLog("[CANCEL] 用户请求取消部署...");
        // 如果在构建阶段，提示将在打包完成后取消
        if ("构建".equals(step)) {
            appendLog("[CANCEL] 当前正在构建，将在打包完成后立即取消");
            return true;
        }
        // 如果在上传阶段，中断上传线程
        Thread ut = uploadThread;
        if (ut != null && ut.isAlive()) {
            appendLog("[CANCEL] 中断 SFTP 上传");
            ut.interrupt();
        }
        return true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void run() {
        boolean success = false;
        boolean wasCancelled = false;
        try {
            appendLog("========== 开始部署 [" + project.getName() + "] ==========");
            Path projectDir = stepPrepareSource();
            checkCancelled("拉取代码完成后");
            stepBuild(projectDir);
            checkCancelled("构建完成后");
            Path artifact = stepResolveArtifact(projectDir);
            checkCancelled("定位产物完成后");
            stepUpload(artifact);
            checkCancelled("上传完成后");
            stepEnsureScript();
            try {
                stepRemoteDeploy();
                success = true;
            } finally {
                stepCleanupScript();
            }
            appendLog("========== 部署成功 ==========");
        } catch (CancelledException e) {
            wasCancelled = true;
            appendLog("[CANCEL] 部署已取消");
            appendLog("========== 部署已取消 ==========");
        } catch (Exception e) {
            if (cancelled) {
                wasCancelled = true;
                appendLog("[CANCEL] 部署已取消（进程被终止）");
                appendLog("========== 部署已取消 ==========");
            } else {
                log.error("部署失败: {}", project.getName(), e);
                appendLog("[ERROR] " + e.getMessage());
                appendLog("========== 部署失败 ==========");
            }
        } finally {
            finish(success, wasCancelled);
        }
    }

    private void checkCancelled(String phase) {
        if (cancelled) {
            appendLog("[CANCEL] " + phase + "检测到取消请求，终止部署");
            throw new CancelledException();
        }
    }

    /** 取消部署专用异常 */
    private static class CancelledException extends RuntimeException {
        CancelledException() { super("部署已取消"); }
    }

    /** 步骤1：clone（已存在则跳过）+ checkout + pull */
    private Path stepPrepareSource() {
        updateStep("拉取代码");
        Path dir = StringUtils.hasText(project.getLocalPath())
                ? Paths.get(project.getLocalPath())
                : Paths.get(props.getWorkspace(), project.getName());
        try {
            if (Files.exists(dir.resolve(".git"))) {
                appendLog("本地仓库已存在，跳过 clone: " + dir);
            } else {
                // 上次 clone 失败的残留目录会导致 git clone 报错，提前给出明确提示
                if (Files.exists(dir)) {
                    try (var entries = Files.list(dir)) {
                        if (entries.findAny().isPresent()) {
                            throw new IllegalStateException("目录 " + dir + " 已存在但不是 git 仓库（可能是上次 clone 失败的残留），请手动删除后重试");
                        }
                    }
                }
                Files.createDirectories(dir.getParent() == null ? dir : dir.getParent());
                CommandUtil.execOrThrow(dir.getParent().toFile(),
                        "git clone " + project.getGitUrl() + " \"" + dir.getFileName() + "\"", this::appendLog);
            }
            File workDir = dir.toFile();
            CommandUtil.execOrThrow(workDir, "git checkout " + project.getBranch(), this::appendLog);
            CommandUtil.execOrThrow(workDir, "git pull", this::appendLog);
            return dir;
        } catch (Exception e) {
            throw new IllegalStateException("拉取代码失败: " + e.getMessage(), e);
        }
    }

    /** 步骤2：执行构建命令 */
    private void stepBuild(Path projectDir) {
        updateStep("构建");
        String cmd = project.getBuildCmd();
        // Java 项目指定了 profile 时追加 -P，覆盖 pom 中 activeByDefault 的默认 profile
        if (Project.TYPE_JAVA.equals(project.getType()) && StringUtils.hasText(project.getBuildProfile())) {
            cmd += " -P " + project.getBuildProfile().trim();
        }
        // npm 项目自动加上 --legacy-peer-deps，避免 peer dependency 冲突
        if (Project.TYPE_VUE.equals(project.getType())) {
            cmd = ensureNpmLegacyPeerDeps(cmd);
        }
        // 传入 currentProcess 引用
        CommandUtil.execOrThrow(projectDir.toFile(), cmd, this::appendLog, currentProcess);
    }

    /** 为 npm install 命令自动追加 --legacy-peer-deps */
    private String ensureNpmLegacyPeerDeps(String cmd) {
        if (cmd == null || !cmd.contains("npm install") || cmd.contains("--legacy-peer-deps")) {
            return cmd;
        }
        return cmd.replaceAll("(?<!\\S)npm\\s+install(?!\\S)", "npm install --legacy-peer-deps");
    }

    /** 步骤5.5：部署命令执行完后，清理目录下脚本文件 */
    private void stepCleanupScript() {
        if (!StringUtils.hasText(project.getScriptName())) {
            return;
        }
        String uploadDir = project.getUploadDir();
        String scriptFile = project.getScriptName();
        String remotePath = uploadDir.endsWith("/") ? uploadDir + scriptFile : uploadDir + "/" + scriptFile;
        appendLog("清理脚本文件: " + remotePath);
        try {
            SshUtil.exec(server.getHost(), server.getPort(), server.getUsername(), serverPassword,
                    "rm -f '" + remotePath + "'", this::appendLog);
        } catch (Exception e) {
            appendLog("[WARN] 清理脚本文件失败: " + e.getMessage());
        }
    }

    /** 步骤3：定位产物；Vue 的 dist 目录压缩为 dist.zip */
    private Path stepResolveArtifact(Path projectDir) {
        updateStep("定位产物");
        String pattern = project.getArtifactPath().replace('\\', '/');
        Path artifact;
        try {
            if (pattern.contains("*")) {
                int idx = pattern.lastIndexOf('/');
                Path parent = idx > 0 ? projectDir.resolve(pattern.substring(0, idx)) : projectDir;
                String glob = idx > 0 ? pattern.substring(idx + 1) : pattern;
                artifact = newestMatch(parent, glob);
            } else {
                artifact = projectDir.resolve(pattern);
            }
            if (artifact == null || !Files.exists(artifact)) {
                throw new IllegalStateException("未找到产物: " + pattern);
            }
            appendLog("产物: " + artifact);
            // Vue 项目：目录 -> zip
            if (Project.TYPE_VUE.equals(project.getType()) && Files.isDirectory(artifact)) {
                Path zip = projectDir.resolve(artifact.getFileName() + ".zip");
                appendLog("压缩 " + artifact.getFileName() + " -> " + zip.getFileName());
                ZipUtil.zipDirectory(artifact, zip);
                return zip;
            }
            return artifact;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("定位产物失败: " + e.getMessage(), e);
        }
    }

    /** 步骤4：SFTP 上传 */
    private void stepUpload(Path artifact) {
        updateStep("上传产物");
        uploadThread = Thread.currentThread();
        try {
            SshUtil.upload(server.getHost(), server.getPort(), server.getUsername(), serverPassword,
                    artifact.toFile(), project.getUploadDir(), this::appendLog);
        } finally {
            uploadThread = null;
        }
    }

    /** 步骤5：检查目录下脚本是否存在，不存在则创建 */
    private void stepEnsureScript() {
        if (!StringUtils.hasText(project.getScriptName())) {
            return;
        }
        updateStep("检查脚本");
        String uploadDir = project.getUploadDir();
        String scriptFile = project.getScriptName();
        String remotePath = uploadDir.endsWith("/") ? uploadDir + scriptFile : uploadDir + "/" + scriptFile;

        // 检查文件是否存在
        String checkCmd = "test -f '" + remotePath + "' && echo 'EXISTS' || echo 'NOT_FOUND'";
        StringBuilder output = new StringBuilder();
        int code = SshUtil.exec(server.getHost(), server.getPort(), server.getUsername(), serverPassword,
                checkCmd, line -> output.append(line).append('\n'));
        if (code != 0) {
            throw new IllegalStateException("检查脚本文件失败(exit=" + code + ")");
        }
        if (output.toString().contains("EXISTS")) {
            appendLog("脚本已存在，跳过创建: " + remotePath);
            return;
        }

        // 不存在则用 scriptContent 创建
        if (!StringUtils.hasText(project.getScriptContent())) {
            appendLog("脚本不存在且未配置脚本内容，跳过创建: " + remotePath);
            return;
        }
        appendLog("脚本不存在，开始创建: " + remotePath);
        // 用 heredoc 写入文件，引号包裹的 SCRIPT_EOF 禁止变量展开，避免转义问题
        String createCmd = "cat > '" + remotePath + "' << 'SCRIPT_EOF'\n"
                + project.getScriptContent() + "\nSCRIPT_EOF";
        int createCode = SshUtil.exec(server.getHost(), server.getPort(), server.getUsername(), serverPassword,
                createCmd, this::appendLog);
        if (createCode != 0) {
            throw new IllegalStateException("创建脚本文件失败(exit=" + createCode + ")");
        }
        // 赋予执行权限
        SshUtil.exec(server.getHost(), server.getPort(), server.getUsername(), serverPassword,
                "chmod +x '" + remotePath + "'", this::appendLog);
        appendLog("脚本创建成功: " + remotePath);
    }

    /** 步骤6：远程执行部署命令（可选） */
    private void stepRemoteDeploy() {
        if (!StringUtils.hasText(project.getDeployCmd())) {
            appendLog("未配置远程部署命令，跳过");
            return;
        }
        updateStep("远程部署");
        appendLog("$ " + project.getDeployCmd());
        // SSH exec 通道是非登录 shell，不加载 /etc/profile，PATH 里没有 java/自装软件；
        // 用 bash -lc 以登录 shell 执行，保证环境变量和手动登录时一致
        String wrapped = "bash -lc '" + project.getDeployCmd().replace("'", "'\\''") + "'";
        int code = SshUtil.exec(server.getHost(), server.getPort(), server.getUsername(), serverPassword,
                wrapped, this::appendLog);
        if (code != 0) {
            throw new IllegalStateException("远程部署命令执行失败(exit=" + code + ")");
        }
    }

    /** 通配符匹配，多个命中时取最近修改的 */
    private Path newestMatch(Path dir, String glob) throws Exception {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        Path newest = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            for (Path p : stream) {
                if (newest == null || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(newest)) > 0) {
                    newest = p;
                }
            }
        }
        return newest;
    }

    private void updateStep(String step) {
        record.setCurrentStep(step);
        recordMapper.updateById(record);
        appendLog("");
        appendLog("===== " + step + " =====");
    }

    private void appendLog(String line) {
        String msg = "[" + LocalDateTime.now().format(TIME_FMT) + "] " + line;
        logBuffer.append(msg).append('\n');
        logHandler.pushLine(record.getId(), msg);
    }

    private void finish(boolean success, boolean cancelled) {
        try {
            if (cancelled) {
                record.setStatus(DeployRecord.STATUS_CANCELLED);
            } else {
                record.setStatus(success ? DeployRecord.STATUS_SUCCESS : DeployRecord.STATUS_FAILED);
            }
            record.setEndTime(LocalDateTime.now());
            if (record.getStartTime() != null) {
                long seconds = Duration.between(record.getStartTime(), record.getEndTime()).getSeconds();
                appendLog("总耗时: " + seconds + " 秒");
            }
            record.setLog(logBuffer.toString());
            recordMapper.updateById(record);
            logHandler.finish(record.getId(), record.getStatus());
        } finally {
            onFinish.run();
        }
    }
}
