package com.coursework.tracker.service;

import com.coursework.tracker.core.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 安全更新：只替换程序 jar，data/ 目录完全不动。
 * 更新前备份数据库；旧 jar 由更新脚本备份为 update-backup.jar。
 */
public final class UpdateService {

    private static final String APP_CLASS = "com/coursework/tracker/App.class";
    private static final String MAIN_CLASS = "com.coursework.tracker.App";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
    private static final String SCRIPT_NAME = "apply-update.bat";
    private static final String STAGED_NAME = "update-staged.jar";
    private static final String OLD_BACKUP_NAME = "update-backup.jar";

    private final BackupService backupService;

    public UpdateService(BackupService backupService) {
        this.backupService = backupService;
    }

    public record PackageInfo(Path file, String version, long size) {
    }

    public record UpdatePlan(Path currentJar, Path stagedJar, Path script, Path databaseBackup,
                             String newVersion) {
    }

    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
                && AppPaths.jarFile() != null;
    }

    public Path currentJar() {
        return AppPaths.jarFile();
    }

    public PackageInfo inspect(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IOException("请选择有效的 jar 文件");
        }
        if (!file.getFileName().toString().toLowerCase().endsWith(".jar")) {
            throw new IOException("更新包必须是 .jar 文件");
        }
        String version;
        try (JarFile jar = new JarFile(file.toFile())) {
            if (jar.getEntry(APP_CLASS) == null) {
                throw new IOException("这不是作业哨兵的安装包（缺少主类）");
            }
            Manifest manifest = jar.getManifest();
            String mainClass = manifest == null ? null
                    : manifest.getMainAttributes().getValue("Main-Class");
            if (mainClass != null && !MAIN_CLASS.equals(mainClass.trim())) {
                throw new IOException("更新包的 Main-Class 不匹配");
            }
            version = manifest == null ? null
                    : manifest.getMainAttributes().getValue("Implementation-Version");
        }
        if (version == null || version.isBlank()) {
            Matcher matcher = VERSION_PATTERN.matcher(file.getFileName().toString());
            version = matcher.find() ? matcher.group(1) : "未知版本";
        }
        return new PackageInfo(file.toAbsolutePath().normalize(), version, Files.size(file));
    }

    public UpdatePlan prepare(PackageInfo pkg) throws IOException {
        Path current = AppPaths.jarFile();
        if (current == null) {
            throw new IOException("开发模式（target/classes）不支持自动更新，请运行打包后的 jar");
        }
        if (current.toAbsolutePath().normalize().equals(pkg.file())) {
            throw new IOException("选择的文件就是当前正在运行的 jar");
        }
        Path dir = current.getParent();
        if (dir == null || !Files.isWritable(dir)) {
            throw new IOException("程序目录不可写，无法自动替换 jar");
        }
        if (!Files.isWritable(current)) {
            throw new IOException("当前 jar 不可写，无法自动替换");
        }

        Path databaseBackup = backupService.backupNow();

        Path staged = dir.resolve(STAGED_NAME);
        Files.copy(pkg.file(), staged, StandardCopyOption.REPLACE_EXISTING);
        Path script = dir.resolve(SCRIPT_NAME);
        Files.writeString(script, scriptContent(current.getFileName().toString()), Charset.defaultCharset());
        return new UpdatePlan(current, staged, script, databaseBackup, pkg.version());
    }

    /** 启动脱离当前 JVM 的更新脚本；调用方随后应立即退出程序。 */
    public void launch(UpdatePlan plan) throws IOException {
        new ProcessBuilder("cmd.exe", "/c", plan.script().toString())
                .directory(plan.currentJar().getParent().toFile())
                .redirectErrorStream(true)
                .start();
    }

    private String scriptContent(String targetName) {
        String javaExe = javaExecutable();
        return """
                @echo off
                setlocal
                set "DIR=%~dp0"
                set "TARGET=%DIR%@TARGET_NAME@"
                set "SOURCE=%DIR%@STAGED_NAME@"
                set "BACKUP=%DIR%@OLD_BACKUP_NAME@"
                set "LOG=%DIR%update.log"

                echo [%date% %time%] update start>>"%LOG%"
                ping 127.0.0.1 -n 3 >nul

                copy /y "%TARGET%" "%BACKUP%" >>"%LOG%" 2>&1
                set /a tries=0
                :retry
                copy /y "%SOURCE%" "%TARGET%" >>"%LOG%" 2>&1
                if not errorlevel 1 goto success
                set /a tries+=1
                if %tries% GEQ 20 goto failed
                ping 127.0.0.1 -n 2 >nul
                goto retry

                :success
                echo [%date% %time%] update success>>"%LOG%"
                del "%SOURCE%" >nul 2>&1
                start "" "@JAVA_EXE@" -Xms32m -Xmx256m -XX:+UseSerialGC -jar "%TARGET%"
                exit /b 0

                :failed
                echo [%date% %time%] update failed>>"%LOG%"
                start "" "@JAVA_EXE@" -jar "%TARGET%"
                exit /b 1
                """
                .replace("@TARGET_NAME@", targetName.replace("%", "%%"))
                .replace("@STAGED_NAME@", STAGED_NAME)
                .replace("@OLD_BACKUP_NAME@", OLD_BACKUP_NAME)
                .replace("@JAVA_EXE@", javaExe.replace("%", "%%"))
                .replace("\n", "\r\n");
    }

    private static String javaExecutable() {
        String command = ProcessHandle.current().info().command().orElse("javaw");
        Path path = Path.of(command);
        Path javaw = path.resolveSibling("javaw.exe");
        return Files.isRegularFile(javaw) ? javaw.toString() : command;
    }
}
