package com.cc.deploy.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;
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
        return exec(workDir, command, logConsumer, null);
    }

    /**
     * 在指定目录执行命令，逐行回调输出，支持存储 Process 引用
     *
     * @param processRef 可选，用于存储 Process 引用
     * @return 进程退出码，0 为成功
     */
    public static int exec(File workDir, String command, Consumer<String> logConsumer,
                           AtomicReference<Process> processRef) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            Process process = pb.start();
            if (processRef != null) {
                processRef.set(process);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), NATIVE_CHARSET))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logConsumer.accept(line);
                }
            }
            int exitCode = process.waitFor();
            if (processRef != null) {
                processRef.set(null);
            }
            return exitCode;
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
        execOrThrow(workDir, command, logConsumer, null);
    }

    /**
     * 执行命令，失败（非0退出码）直接抛异常，支持存储 Process 引用
     */
    public static void execOrThrow(File workDir, String command, Consumer<String> logConsumer,
                                   AtomicReference<Process> processRef) {
        logConsumer.accept("$ " + command);
        int code = exec(workDir, command, logConsumer, processRef);
        if (code != 0) {
            throw new IllegalStateException("命令执行失败(exit=" + code + "): " + command);
        }
    }
}
