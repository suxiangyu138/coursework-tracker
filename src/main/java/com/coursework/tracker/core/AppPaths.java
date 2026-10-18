package com.coursework.tracker.core;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.logging.Logger;

/**
 * 应用路径解析：默认把数据与日志放在程序同级的 {@code data/}、{@code logs/} 下，
 * 便于绿色运行与备份；若程序目录不可写（例如装在 Program Files），回退到用户目录。
 */
public final class AppPaths {

    private static final Logger LOG = Logger.getLogger(AppPaths.class.getName());
    private static final String FALLBACK_DIR = "CourseWorkTracker";

    private static volatile Path baseDir;
    private static volatile Path dataDir;
    private static volatile Path logDir;
    private static volatile boolean fallbackUsed;

    private AppPaths() {
    }

    /** 初始化目录，可重复调用（幂等）。 */
    public static synchronized void init() {
        if (dataDir != null) {
            return;
        }
        Path base = resolveBaseDir();
        Path data = base.resolve("data");
        if (!isWritableDirectory(data)) {
            Path fallback = fallbackBaseDir();
            LOG.warning("程序目录不可写，数据改存到: " + fallback);
            base = fallback;
            data = base.resolve("data");
            fallbackUsed = true;
        }
        dataDir = ensureDirectory(data);
        logDir = ensureDirectory(base.resolve("logs"));
        baseDir = base;
        LOG.info("数据目录: " + dataDir);
    }

    private static Path resolveBaseDir() {
        try {
            CodeSource source = AppPaths.class.getProtectionDomain().getCodeSource();
            if (source != null) {
                Path location = Paths.get(source.getLocation().toURI());
                if (Files.isRegularFile(location) && location.getParent() != null) {
                    // 以 jar 运行时，取 jar 所在目录
                    return location.getParent().toAbsolutePath().normalize();
                }
            }
        } catch (URISyntaxException | RuntimeException e) {
            LOG.warning("无法定位程序目录，使用当前工作目录: " + e.getMessage());
        }
        return Paths.get("").toAbsolutePath().normalize();
    }

    private static Path fallbackBaseDir() {
        String appData = System.getenv("APPDATA");
        Path root = (appData != null && !appData.isBlank())
                ? Paths.get(appData)
                : Paths.get(System.getProperty("user.home", "."));
        return root.resolve(FALLBACK_DIR).toAbsolutePath().normalize();
    }

    private static boolean isWritableDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, ".probe", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private static Path ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            return dir.toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new IllegalStateException("无法创建目录: " + dir, e);
        }
    }

    public static Path baseDir() {
        init();
        return baseDir;
    }

    public static Path dataDir() {
        init();
        return dataDir;
    }

    public static Path logDir() {
        init();
        return logDir;
    }

    public static Path databaseFile() {
        return dataDir().resolve("coursework.db");
    }

    /** 当前运行的 jar 文件；开发态（target/classes）返回 null。 */
    public static Path jarFile() {
        try {
            CodeSource source = AppPaths.class.getProtectionDomain().getCodeSource();
            if (source != null) {
                Path location = Paths.get(source.getLocation().toURI());
                if (Files.isRegularFile(location)) {
                    return location.toAbsolutePath().normalize();
                }
            }
        } catch (URISyntaxException | RuntimeException e) {
            LOG.warning("无法定位当前 jar: " + e.getMessage());
        }
        return null;
    }

    public static Path logFile() {
        return logDir().resolve("app.log");
    }

    public static Path lockFile() {
        return dataDir().resolve(".lock");
    }

    public static boolean isFallbackUsed() {
        return fallbackUsed;
    }
}
