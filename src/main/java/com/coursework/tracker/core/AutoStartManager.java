package com.coursework.tracker.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Windows 开机自启管理：写入/删除
 * {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Run} 下的启动项。
 *
 * <p>只对打包后的 jar 生效（开发模式运行 target/classes 时无法确定启动命令），
 * 启动命令固定为：
 * <pre>
 * javaw -Xms32m -Xmx256m -XX:+UseSerialGC -jar &lt;jar 绝对路径&gt; --minimized
 * </pre>
 */
public final class AutoStartManager {

    private static final Logger LOG = Logger.getLogger(AutoStartManager.class.getName());
    private static final String REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "CourseWorkTracker";
    private static final String HEAP_OPTIONS = "-Xms32m -Xmx256m -XX:+UseSerialGC";

    private AutoStartManager() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** 当前是否以 jar 方式运行；开发模式（target/classes）返回 null。 */
    public static Path currentJar() {
        try {
            var source = AutoStartManager.class.getProtectionDomain().getCodeSource();
            if (source == null) {
                return null;
            }
            Path location = Path.of(source.getLocation().toURI());
            if (Files.isRegularFile(location)
                    && location.getFileName().toString().toLowerCase().endsWith(".jar")) {
                return location.toAbsolutePath().normalize();
            }
        } catch (Exception e) {
            LOG.fine("无法定位 jar 路径: " + e.getMessage());
        }
        return null;
    }

    public static boolean isSupported() {
        return isWindows() && currentJar() != null;
    }

    public static boolean isEnabled() {
        if (!isWindows()) {
            return false;
        }
        try {
            return exec(List.of("reg", "query", REG_KEY, "/v", VALUE_NAME)).exitCode() == 0;
        } catch (IOException e) {
            LOG.fine("读取开机自启状态失败: " + e.getMessage());
            return false;
        }
    }

    /** 启动命令预览（用于设置界面与文档说明）。 */
    public static String commandLine() {
        Path jar = currentJar();
        if (jar == null) {
            return "(开发模式不可用，请使用打包后的 jar)";
        }
        return "\"" + javaW() + "\" " + HEAP_OPTIONS + " -jar \"" + jar + "\" --minimized";
    }

    /** 写入注册表启动项；失败抛出 IOException 并带上 reg 输出。 */
    public static void enable() throws IOException {
        Path jar = currentJar();
        if (!isWindows()) {
            throw new IOException("当前系统不支持开机自启（仅 Windows）");
        }
        if (jar == null) {
            throw new IOException("开发模式无法注册开机自启，请先执行 mvn clean package 并使用 jar 运行");
        }
        String command = "\"" + javaW() + "\" " + HEAP_OPTIONS + " -jar \"" + jar + "\" --minimized";

        // reg add 对含引号的 /d 值转义很挑剔，改用 .reg 文件导入，避免命令行解析问题
        Path regFile = Files.createTempFile("coursework-autostart-", ".reg");
        try {
            String escaped = command.replace("\\", "\\\\").replace("\"", "\\\"");
            String content = "Windows Registry Editor Version 5.00\r\n\r\n"
                    + "[HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run]\r\n"
                    + "\"" + VALUE_NAME + "\"=\"" + escaped + "\"\r\n";
            byte[] body = content.getBytes(StandardCharsets.UTF_16LE);
            byte[] withBom = new byte[body.length + 2];
            withBom[0] = (byte) 0xFF;
            withBom[1] = (byte) 0xFE;
            System.arraycopy(body, 0, withBom, 2, body.length);
            Files.write(regFile, withBom);

            Result result = exec(List.of("reg", "import", regFile.toString()));
            if (result.exitCode() != 0) {
                throw new IOException("写入注册表失败: " + result.output().trim());
            }
        } finally {
            try {
                Files.deleteIfExists(regFile);
            } catch (IOException e) {
                LOG.fine("删除临时 reg 文件失败: " + e.getMessage());
            }
        }
        LOG.info("已开启开机自启: " + command);
    }

    public static void disable() throws IOException {
        if (!isWindows()) {
            throw new IOException("当前系统不支持开机自启（仅 Windows）");
        }
        if (!isEnabled()) {
            return;
        }
        Result result = exec(List.of("reg", "delete", REG_KEY, "/v", VALUE_NAME, "/f"));
        if (result.exitCode() != 0) {
            throw new IOException("删除注册表项失败: " + result.output().trim());
        }
        LOG.info("已关闭开机自启");
    }

    private static String javaW() {
        Path home = Path.of(System.getProperty("java.home"));
        Path javaw = home.resolve("bin").resolve("javaw.exe");
        if (Files.isRegularFile(javaw)) {
            return javaw.toString();
        }
        return home.resolve("bin").resolve("java.exe").toString();
    }

    private record Result(int exitCode, String output) {
    }

    private static Result exec(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            int exitCode = process.waitFor();
            return new Result(exitCode, output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("执行命令被中断", e);
        }
    }
}
