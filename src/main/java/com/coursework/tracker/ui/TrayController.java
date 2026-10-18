package com.coursework.tracker.ui;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.model.PlannedReminder;

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 托盘常驻：图标、右键菜单、气泡提醒。系统不支持托盘时降级为普通窗口模式。
 */
public final class TrayController {

    private static final Logger LOG = Logger.getLogger(TrayController.class.getName());

    private final MainFrame frame;
    private final AppContext context;

    private TrayIcon trayIcon;
    private boolean installed;
    private boolean minimizedHintShown;

    public TrayController(MainFrame frame, AppContext context) {
        this.frame = frame;
        this.context = context;
    }

    public boolean install() {
        if (!SystemTray.isSupported()) {
            LOG.info("当前系统不支持托盘，使用普通窗口模式");
            return false;
        }
        try {
            trayIcon = new TrayIcon(createIcon(), "作业哨兵", buildMenu());
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> frame.showFromTray());
            SystemTray.getSystemTray().add(trayIcon);
            installed = true;
            return true;
        } catch (AWTException | RuntimeException e) {
            LOG.log(Level.WARNING, "托盘初始化失败", e);
            return false;
        }
    }

    private PopupMenu buildMenu() {
        PopupMenu menu = new PopupMenu();

        MenuItem open = new MenuItem("显示主界面");
        open.addActionListener(e -> frame.showFromTray());

        MenuItem check = new MenuItem("立即检查提醒");
        check.addActionListener(e -> frame.checkRemindersNow());

        MenuItem center = new MenuItem("提醒中心");
        center.addActionListener(e -> frame.showReminderCenter());

        MenuItem mute = new MenuItem("静音 1 小时");
        mute.addActionListener(e -> {
            context.reminderService().muteFor(Duration.ofHours(1));
            frame.showToast("已静音 1 小时");
        });

        MenuItem unmute = new MenuItem("取消静音");
        unmute.addActionListener(e -> {
            context.reminderService().clearMute();
            frame.showToast("已恢复提醒");
        });

        MenuItem exit = new MenuItem("退出");
        exit.addActionListener(e -> frame.exitApplication());

        menu.add(open);
        menu.add(check);
        menu.add(center);
        menu.addSeparator();
        menu.add(mute);
        menu.add(unmute);
        menu.addSeparator();
        menu.add(exit);
        return menu;
    }

    /** 发送气泡提醒（多条合并）。 */
    public void notifyReminders(List<PlannedReminder> reminders) {
        if (reminders == null || reminders.isEmpty()) {
            return;
        }
        String message = formatMessage(reminders);
        if (installed && trayIcon != null) {
            trayIcon.displayMessage("作业哨兵 · 作业提醒", message, TrayIcon.MessageType.INFO);
        }
        if (context.settings().soundEnabled()) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private static String formatMessage(List<PlannedReminder> reminders) {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(3, reminders.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(reminders.get(i).message());
        }
        if (reminders.size() > limit) {
            builder.append("\n… 共 ").append(reminders.size()).append(" 条");
        }
        return builder.toString();
    }

    public void updateTooltip(String text) {
        if (trayIcon != null) {
            trayIcon.setToolTip(text);
        }
    }

    public void notifyMinimized() {
        if (!installed || minimizedHintShown || trayIcon == null) {
            return;
        }
        minimizedHintShown = true;
        trayIcon.displayMessage("作业哨兵仍在运行",
                "窗口已最小化到托盘，作业提醒会继续生效。", TrayIcon.MessageType.INFO);
    }

    /** 开机自启（--minimized）时的一次性提示。 */
    public void notifyStarted() {
        if (trayIcon == null) {
            return;
        }
        trayIcon.displayMessage("作业哨兵已在后台运行",
                "双击托盘图标打开主界面；作业提醒会继续生效。", TrayIcon.MessageType.INFO);
    }

    public void testNotification() {
        if (trayIcon != null) {
            trayIcon.displayMessage("作业哨兵 · 测试提醒",
                    "这是一条测试提醒：实验三 还有 2 小时截止", TrayIcon.MessageType.INFO);
        }
        if (context.settings().soundEnabled()) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    public boolean isInstalled() {
        return installed;
    }

    public void dispose() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
            installed = false;
        }
    }

    /** 画一个时钟图标：蓝底圆 + 白色指针。 */
    private static Image createIcon() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x2563EB));
        g.fillOval(2, 2, size - 4, size - 4);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(5, 5, size - 10, size - 10);
        g.drawLine(size / 2, size / 2, size / 2, 10);
        g.drawLine(size / 2, size / 2, size - 10, size / 2 + 4);
        g.dispose();
        return image;
    }
}
