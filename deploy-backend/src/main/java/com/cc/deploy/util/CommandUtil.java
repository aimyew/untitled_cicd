package com.cc.deploy.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.function.Consumer;

/**
 * 本地命令执行（git / mvn / npm 等）
 * <p>Windows 下 mvn、npm 是 .cmd 批处理，统一通过 cmd /c 执行
 */
public class CommandUtil {

    /** Windows 中文环境控制台输出通常是 GBK，按平台原生编码解码 */
    private static final Charset NATIVE_CHARSET =
            Charset.forName(System.getProperty("native.encoding", "GBK"));

    /**
     * 在指定目录执行命令，逐行回调输出
     *
     * @return 进程退出码，0 为成功
     */
    public static int exec(File workDir, String command, Consumer<String> logConsumer) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            // 无交互终端，禁止 git 弹出账号密码提示，让它直接报出可读的错误
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), NATIVE_CHARSET))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logConsumer.accept(line);
                }
            }
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("命令被中断: " + command, e);
        } catch (Exception e) {
            throw new IllegalStateException("命令执行失败: " + command + "，原因: " + e.getMessage(), e);
        }
    }

    /**
     * 执行命令，失败（非0退出码）直接抛异常
     */
    public static void execOrThrow(File workDir, String command, Consumer<String> logConsumer) {
        logConsumer.accept("$ " + command);
        int code = exec(workDir, command, logConsumer);
        if (code != 0) {
            throw new IllegalStateException("命令执行失败(exit=" + code + "): " + command);
        }
    }
}
