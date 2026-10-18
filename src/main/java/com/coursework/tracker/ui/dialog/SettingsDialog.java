package com.coursework.tracker.ui.dialog;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.AutoStartManager;
import com.coursework.tracker.service.Settings;
import com.coursework.tracker.ui.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** 设置：提醒开关、静音时段、视野窗口、主题。 */
public final class SettingsDialog extends JDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppContext context;
    private final Runnable onThemeChanged;

    private final JCheckBox reminderCheck = new JCheckBox("开启定时提醒（T-24h / T-6h / T-1h / T-15min）");
    private final JCheckBox soundCheck = new JCheckBox("提醒时播放提示音");
    private final JCheckBox autoStartCheck = new JCheckBox("开机自启（登录后最小化到托盘）");
    private final boolean autoStartSupported = AutoStartManager.isSupported();
    private final JTextField quietStartField = new JTextField(5);
    private final JTextField quietEndField = new JTextField(5);
    private final JSpinner horizonSpinner = new JSpinner(new SpinnerNumberModel(168, 24, 720, 24));
    private final JComboBox<String> themeCombo = new JComboBox<>(new String[]{"浅色", "深色"});
    private final JLabel errorLabel = new JLabel(" ");

    private SettingsDialog(Window owner, AppContext context, Runnable onThemeChanged) {
        super(owner, "设置", ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.onThemeChanged = onThemeChanged;
        buildUi();
        loadValues();
        pack();
        setLocationRelativeTo(owner);
    }

    public static void show(Window owner, AppContext context, Runnable onThemeChanged) {
        new SettingsDialog(owner, context, onThemeChanged).setVisible(true);
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 5, 6, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        form.add(reminderCheck, gbc);

        gbc.gridy++;
        form.add(soundCheck, gbc);

        gbc.gridy++;
        autoStartCheck.setEnabled(autoStartSupported);
        autoStartCheck.setToolTipText(autoStartSupported
                ? AutoStartManager.commandLine()
                : "仅支持 Windows，且需以打包后的 jar 方式运行");
        form.add(autoStartCheck, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        form.add(new JLabel("静音时段"), gbc);
        JPanel quietPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        quietPanel.setOpaque(false);
        quietPanel.add(quietStartField);
        quietPanel.add(new JLabel("至"));
        quietPanel.add(quietEndField);
        quietPanel.add(new JLabel("（格式 HH:mm，跨天自动识别）"));
        gbc.gridx = 1;
        form.add(quietPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        form.add(new JLabel("紧急度视野（小时）"), gbc);
        gbc.gridx = 1;
        form.add(horizonSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        form.add(new JLabel("主题"), gbc);
        gbc.gridx = 1;
        form.add(themeCombo, gbc);

        errorLabel.setForeground(new java.awt.Color(0xC62828));
        errorLabel.setFont(errorLabel.getFont().deriveFont(Font.PLAIN, 12f));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        form.add(errorLabel, gbc);

        JButton cancel = new JButton("取消");
        cancel.addActionListener(e -> dispose());
        JButton save = new JButton("保存");
        save.addActionListener(e -> save());
        getRootPane().setDefaultButton(save);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        buttons.add(cancel);
        buttons.add(save);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void loadValues() {
        Settings settings = context.settings();
        reminderCheck.setSelected(settings.remindersEnabled());
        soundCheck.setSelected(settings.soundEnabled());
        autoStartCheck.setSelected(autoStartSupported && AutoStartManager.isEnabled());
        quietStartField.setText(TIME.format(settings.quietStart()));
        quietEndField.setText(TIME.format(settings.quietEnd()));
        horizonSpinner.setValue((int) settings.horizonHours());
        themeCombo.setSelectedIndex(ThemeManager.isDark() ? 1 : 0);
    }

    private void save() {
        try {
            LocalTime start = LocalTime.parse(quietStartField.getText().trim(), TIME);
            LocalTime end = LocalTime.parse(quietEndField.getText().trim(), TIME);
            Settings settings = context.settings();
            settings.setRemindersEnabled(reminderCheck.isSelected());
            settings.setQuietWindow(start, end);
            settings.setHorizonHours(((Number) horizonSpinner.getValue()).doubleValue());
            context.settingDao().setBoolean("reminder.sound", soundCheck.isSelected());

            if (autoStartSupported) {
                boolean enabled = AutoStartManager.isEnabled();
                if (autoStartCheck.isSelected() != enabled) {
                    if (autoStartCheck.isSelected()) {
                        AutoStartManager.enable();
                    } else {
                        AutoStartManager.disable();
                    }
                }
            }

            String theme = themeCombo.getSelectedIndex() == 1 ? ThemeManager.DARK : ThemeManager.LIGHT;
            boolean themeChanged = !theme.equalsIgnoreCase(settings.theme());
            settings.setTheme(theme);
            if (themeChanged) {
                ThemeManager.install(theme);
                ThemeManager.applyTo(getOwner());
                if (onThemeChanged != null) {
                    onThemeChanged.run();
                }
            }
            dispose();
        } catch (DateTimeParseException e) {
            errorLabel.setText("静音时间格式应为 HH:mm，例如 23:00");
        } catch (IOException e) {
            errorLabel.setText("开机自启设置失败：" + e.getMessage());
        }
    }
}
