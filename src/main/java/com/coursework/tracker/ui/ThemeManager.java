package com.coursework.tracker.ui;

import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Countdown;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Window;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 外观与颜色语义：浅色/深色主题、倒计时与状态的配色。 */
public final class ThemeManager {

    private static final Logger LOG = Logger.getLogger(ThemeManager.class.getName());

    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private static volatile boolean dark;

    private ThemeManager() {
    }

    public static void install(String theme) {
        boolean wantDark = DARK.equalsIgnoreCase(theme);
        try {
            if (wantDark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));
            UIManager.put("Component.arc", 10);
            UIManager.put("Button.arc", 10);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ScrollBar.width", 12);
            UIManager.put("Table.rowHeight", 32);
            dark = wantDark;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "应用主题失败，使用默认外观", e);
        }
    }

    public static void applyTo(Window window) {
        if (window != null) {
            SwingUtilities.updateComponentTreeUI(window);
            window.repaint();
        }
    }

    public static boolean isDark() {
        return dark;
    }

    public static Color alternateRow() {
        return dark ? new Color(0x2B2D30) : new Color(0xF6F8FB);
    }

    public static Color panelBackground() {
        return dark ? new Color(0x1F2124) : new Color(0xFBFCFE);
    }

    public static Color mutedText() {
        return dark ? new Color(0x9AA0A6) : new Color(0x6B7280);
    }

    public static Color cardBackground() {
        return dark ? new Color(0x2A2D31) : new Color(0xFFFFFF);
    }

    public static Color cardBorder() {
        return dark ? new Color(0x3A3D42) : new Color(0xE3E8EF);
    }

    /** 倒计时颜色：逾期深红、24 小时内红、3 天内橙、7 天内黄、其余绿、已完成灰。 */
    public static Color countdownColor(Countdown countdown, boolean completed) {
        if (completed) {
            return dark ? new Color(0x757575) : new Color(0x9E9E9E);
        }
        if (countdown.overdue()) {
            return dark ? new Color(0xEF5350) : new Color(0xC62828);
        }
        long totalHours = countdown.days() * 24 + countdown.hours();
        if (totalHours < 24) {
            return dark ? new Color(0xFF7043) : new Color(0xE53935);
        }
        if (totalHours < 72) {
            return dark ? new Color(0xFFA726) : new Color(0xFB8C00);
        }
        if (totalHours < 168) {
            return dark ? new Color(0xFFD54F) : new Color(0xF9A825);
        }
        return dark ? new Color(0x81C784) : new Color(0x2E7D32);
    }

    public static Color statusColor(AssignmentStatus status) {
        return switch (status) {
            case TODO -> dark ? new Color(0xB0BEC5) : new Color(0x607D8B);
            case DOING -> dark ? new Color(0x64B5F6) : new Color(0x1565C0);
            case DONE -> dark ? new Color(0x81C784) : new Color(0x2E7D32);
            case SUBMITTED -> dark ? new Color(0x4DD0E1) : new Color(0x00838F);
        };
    }

    public static Color urgencyColor(double score) {
        if (score < 0) {
            return dark ? new Color(0x616161) : new Color(0xBDBDBD);
        }
        if (score >= 0.8) {
            return dark ? new Color(0xEF5350) : new Color(0xC62828);
        }
        if (score >= 0.55) {
            return dark ? new Color(0xFFA726) : new Color(0xF57C00);
        }
        if (score >= 0.3) {
            return dark ? new Color(0xFFD54F) : new Color(0xF9A825);
        }
        return dark ? new Color(0x81C784) : new Color(0x388E3C);
    }

    public static void styleCell(JComponent component, javax.swing.JTable table,
                                 int row, boolean selected) {
        if (selected) {
            component.setBackground(table.getSelectionBackground());
        } else if (row % 2 == 1) {
            component.setBackground(alternateRow());
        } else {
            component.setBackground(table.getBackground());
        }
    }
}
