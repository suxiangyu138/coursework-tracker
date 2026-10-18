package com.coursework.tracker;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.AppPaths;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.core.LoggingSetup;
import com.coursework.tracker.core.SingleInstanceGuard;
import com.coursework.tracker.ui.MainFrame;
import com.coursework.tracker.ui.ThemeManager;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 程序入口。
 *
 * <pre>
 * java -jar coursework-tracker-1.0.0.jar              正常启动（托盘常驻）
 * java -jar coursework-tracker-1.0.0.jar --selftest    无界面自检：建库、造数据、算成绩、跑提醒
 * java -jar coursework-tracker-1.0.0.jar --seed-demo  仅生成示例数据后退出
 * </pre>
 */
public final class App {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    private App() {
    }

    public static void main(String[] args) {
        AppPaths.init();
        LoggingSetup.init(AppPaths.logDir());

        if (Arrays.asList(args).contains("--selftest")) {
            System.exit(SelfTest.run());
        }

        if (Arrays.asList(args).contains("--seed-demo")) {
            try (AppContext context = AppContext.open(AppPaths.databaseFile())) {
                int created = context.demoDataFactory().generate(ClockProvider.now());
                System.out.println("[seed] 已生成 " + created + " 条示例作业");
                System.out.println("[seed] 数据文件: " + AppPaths.databaseFile());
            }
            System.exit(0);
        }

        SingleInstanceGuard guard = SingleInstanceGuard.acquire(AppPaths.lockFile());
        if (!guard.isAcquired()) {
            JOptionPane.showMessageDialog(null,
                    "作业哨兵已在运行，请从任务栏托盘打开主界面。",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        AppContext context;
        try {
            context = AppContext.open(AppPaths.databaseFile());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "启动失败", e);
            JOptionPane.showMessageDialog(null,
                    "启动失败：" + e.getMessage() + "\n\n日志：" + AppPaths.logFile(),
                    "作业哨兵", JOptionPane.ERROR_MESSAGE);
            guard.close();
            return;
        }

        ThemeManager.install(context.settings().theme());

        String screenshotPath = null;
        for (String arg : args) {
            if (arg.startsWith("--screenshot=")) {
                screenshotPath = arg.substring("--screenshot=".length());
            }
        }
        if (screenshotPath != null) {
            captureScreenshot(context, screenshotPath);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(context);
            frame.setVisible(true);
            frame.installTray();
            context.reminderService().start();
            frame.firstRunHint();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                context.close();
            } catch (RuntimeException ignored) {
                // 退出阶段异常不再抛出
            }
            guard.close();
        }, "shutdown-hook"));

        LOG.info("作业哨兵启动完成，数据文件=" + AppPaths.databaseFile());
    }

    /** 渲染主窗口并保存 PNG，用于文档截图与无人工验收。 */
    private static void captureScreenshot(AppContext context, String path) {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(context);
            frame.setVisible(true);
            frame.installTray();
            context.reminderService().start();

            javax.swing.Timer timer = new javax.swing.Timer(2500, event -> {
                try {
                    javax.swing.JRootPane root = frame.getRootPane();
                    java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                            root.getWidth(), root.getHeight(),
                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D graphics = image.createGraphics();
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    root.paintAll(graphics);
                    graphics.dispose();
                    java.nio.file.Path target = java.nio.file.Paths.get(path).toAbsolutePath();
                    if (target.getParent() != null) {
                        java.nio.file.Files.createDirectories(target.getParent());
                    }
                    javax.imageio.ImageIO.write(image, "png", target.toFile());
                    System.out.println("[screenshot] " + target + " "
                            + image.getWidth() + "x" + image.getHeight());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                frame.exitApplication();
            });
            timer.setRepeats(false);
            timer.start();
        });
    }
}
