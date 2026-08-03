package com.cc.deploy.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zip 压缩工具（Vue 的 dist 目录打包为 dist.zip）
 */
public class ZipUtil {

    /**
     * 把目录压缩成 zip，zip 内部以目录名为根（dist/xxx 结构）
     */
    public static void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        Files.deleteIfExists(zipFile);
        String rootName = sourceDir.getFileName().toString();
        try (OutputStream fos = Files.newOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos);
             Stream<Path> walk = Files.walk(sourceDir)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String entryName = rootName + "/" + sourceDir.relativize(file).toString().replace('\\', '/');
                try {
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new IllegalStateException("压缩失败: " + file, e);
                }
            });
        }
    }
}
