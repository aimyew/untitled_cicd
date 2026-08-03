package com.cc.deploy.util;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpProgressMonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * SSH / SFTP 工具（密码登录），替代 Xftp 手工上传
 */
public class SshUtil {

    private static final int CONNECT_TIMEOUT = 10_000;

    /** 测试连接，失败抛异常 */
    public static void testConnect(String host, int port, String username, String password) throws Exception {
        Session session = openSession(host, port, username, password);
        session.disconnect();
    }

    /**
     * SFTP 上传本地文件到远程目录（目录不存在会自动 mkdir -p）
     */
    public static void upload(String host, int port, String username, String password,
                              File localFile, String remoteDir, Consumer<String> log) {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(host, port, username, password);
            // 确保远程目录存在
            execInner(session, "mkdir -p " + remoteDir, log);

            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT);
            String remotePath = remoteDir.endsWith("/") ? remoteDir + localFile.getName()
                    : remoteDir + "/" + localFile.getName();
            log.accept("开始上传: " + localFile.getAbsolutePath()
                    + " (" + String.format("%.2f", localFile.length() / 1024.0 / 1024.0) + " MB)");
            sftp.put(localFile.getAbsolutePath(), remotePath, new ProgressLogger(localFile.length(), log));
            log.accept("上传完成: " + remotePath);
        } catch (Exception e) {
            throw new IllegalStateException("SFTP 上传失败: " + e.getMessage(), e);
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /**
     * 远程执行命令，返回退出码
     */
    public static int exec(String host, int port, String username, String password,
                           String command, Consumer<String> log) {
        Session session = null;
        try {
            session = openSession(host, port, username, password);
            return execInner(session, command, log);
        } catch (Exception e) {
            throw new IllegalStateException("远程命令执行失败: " + e.getMessage(), e);
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private static Session openSession(String host, int port, String username, String password) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(CONNECT_TIMEOUT);
        return session;
    }

    private static int execInner(Session session, String command, Consumer<String> log) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        try {
            channel.setCommand(command);
            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();
            channel.connect(CONNECT_TIMEOUT);
            readLines(stdout, log);
            readLines(stderr, log);
            // 等待通道关闭以拿到准确退出码
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }
            return channel.getExitStatus();
        } finally {
            channel.disconnect();
        }
    }

    private static void readLines(InputStream in, Consumer<String> log) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.accept(line);
            }
        }
    }

    /** 上传进度：每 20% 打一行日志 */
    private static class ProgressLogger implements SftpProgressMonitor {
        private final long total;
        private final Consumer<String> log;
        private long transferred = 0;
        private int lastPercent = 0;

        ProgressLogger(long total, Consumer<String> log) {
            this.total = total;
            this.log = log;
        }

        @Override
        public void init(int op, String src, String dest, long max) {
        }

        @Override
        public boolean count(long count) {
            transferred += count;
            if (total > 0) {
                int percent = (int) (transferred * 100 / total);
                if (percent - lastPercent >= 20) {
                    lastPercent = percent;
                    log.accept("上传进度: " + percent + "%");
                }
            }
            return true;
        }

        @Override
        public void end() {
        }
    }
}
