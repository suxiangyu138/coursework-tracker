package com.coursework.tracker.ui;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.model.PlannedReminder;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 托盘常驻：图标、右键菜单、气泡提醒。系统不支持托盘时降级为普通窗口模式。
 *
 * <p>右键菜单使用 Swing {@link JPopupMenu} 而不是 AWT {@code PopupMenu}：
 * Windows 原生菜单固定用系统菜单字体（英文系统下是 Segoe UI），没有中文字形，
 * 会显示成一排方块；Swing 菜单跟随 FlatLaf 字体，不会出现该问题。
 */
public final class TrayController {

    private static final Logger LOG = Logger.getLogger(TrayController.class.getName());

    private final MainFrame frame;
    private final AppContext context;

    private TrayIcon trayIcon;
    private boolean installed;
    private boolean minimizedHintShown;
    private JPopupMenu trayMenu;
    private JWindow menuHost;

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
            trayIcon = new TrayIcon(AppIcon.trayIcon(), "作业哨兵");
            trayIcon.setImageAutoSize(true);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger() || SwingUtilities.isRightMouseButton(event)) {
                        showTrayMenu();
                    }
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 1) {
                        showTrayMenu();
                    }
                }
            });
            SystemTray.getSystemTray().add(trayIcon);
            installed = true;
            return true;
        } catch (AWTException | RuntimeException e) {
            LOG.log(Level.WARNING, "托盘初始化失败", e);
            return false;
        }
    }

    /** 在鼠标位置附近弹出 Swing 托盘菜单，避开屏幕边缘。 */
    private void showTrayMenu() {
        if (trayMenu != null && trayMenu.isVisible()) {
            trayMenu.setVisible(false);
            return;
        }
        JPopupMenu menu = buildMenu();
        Dimension size = menu.getPreferredSize();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Point pointer = MouseInfo.getPointerInfo() == null
                ? new Point(screen.x + screen.width - 80, screen.y + screen.height - 80)
                : MouseInfo.getPointerInfo().getLocation();

        int x = Math.max(screen.x + 4,
                Math.min(pointer.x - size.width / 2, screen.x + screen.width - size.width - 8));
        int y = pointer.y - size.height - 12;
        if (y < screen.y + 4) {
            y = Math.min(pointer.y + 12, screen.y + screen.height - size.height - 8);
        }

        JWindow host = new JWindow();
        host.setAlwaysOnTop(true);
        host.setFocusableWindowState(false);
        host.setLocation(x, y);
        host.setSize(1, 1);
        host.setVisible(true);

        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                // 不需要额外处理
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                host.dispose();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
                host.dispose();
            }
        });
        menu.show(host, 0, 0);
        trayMenu = menu;
        menuHost = host;
    }

    private JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("显示主界面", frame::showFromTray));
        menu.add(menuItem("立即检查提醒", frame::checkRemindersNow));
        menu.add(menuItem("提醒中心", frame::showReminderCenter));
        menu.addSeparator();
        menu.add(menuItem("静音 1 小时", () -> {
            context.reminderService().muteFor(Duration.ofHours(1));
            frame.showToast("已静音 1 小时");
        }));
        menu.add(menuItem("取消静音", () -> {
            context.reminderService().clearMute();
            frame.showToast("已恢复提醒");
        }));
        menu.addSeparator();
        menu.add(menuItem("退出", frame::exitApplication));
        return menu;
    }

    private static JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
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
                "点击托盘图标打开菜单；作业提醒会继续生效。", TrayIcon.MessageType.INFO);
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
        if (menuHost != null) {
            menuHost.dispose();
            menuHost = null;
        }
        if (trayMenu != null) {
            trayMenu.setVisible(false);
            trayMenu = null;
        }
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
            installed = false;
        }
    }
}
