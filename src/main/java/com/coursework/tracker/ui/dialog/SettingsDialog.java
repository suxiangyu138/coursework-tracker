package com.coursework.tracker.ui.dialog;

import com.coursework.tracker.ai.AiConfig;
import com.coursework.tracker.ai.DeepSeekClient;
import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.AutoStartManager;
import com.coursework.tracker.service.Settings;
import com.coursework.tracker.ui.AppIcon;
import com.coursework.tracker.ui.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
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

    private final JCheckBox aiEnabledCheck = new JCheckBox("启用 AI 助手（DeepSeek）");
    private final JPasswordField aiKeyField = new JPasswordField(30);
    private final JTextField aiBaseField = new JTextField(30);
    private final JTextField aiModelField = new JTextField(24);
    private final JButton aiTestButton = new JButton("测试连接");
    private final JLabel aiStatusLabel = new JLabel(" ");

    private SettingsDialog(Window owner, AppContext context, Runnable onThemeChanged) {
        super(owner, "设置", ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.onThemeChanged = onThemeChanged;
        buildUi();
        loadValues();
        pack();
        setLocationRelativeTo(owner);
        AppIcon.applyTo(this);
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

        // ----- AI 助手 -----
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JLabel aiTitle = new JLabel("AI 助手（DeepSeek）");
        aiTitle.setFont(aiTitle.getFont().deriveFont(Font.BOLD));
        form.add(aiTitle, gbc);

        gbc.gridy++;
        form.add(aiEnabledCheck, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        form.add(new JLabel("API Key"), gbc);
        gbc.gridx = 1;
        form.add(aiKeyField, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        form.add(new JLabel("接口地址"), gbc);
        gbc.gridx = 1;
        form.add(aiBaseField, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        form.add(new JLabel("模型"), gbc);
        gbc.gridx = 1;
        form.add(aiModelField, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JPanel aiTestPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        aiTestPanel.setOpaque(false);
        aiStatusLabel.setForeground(new java.awt.Color(0x6B7280));
        // 固定状态区宽度：错误信息可能很长，JLabel 会撑大 GridBagLayout，导致整个设置页错位
        aiStatusLabel.setPreferredSize(new Dimension(340, 18));
        aiStatusLabel.setMinimumSize(new Dimension(340, 18));
        aiStatusLabel.setToolTipText(null);
        aiTestPanel.add(aiTestButton);
        aiTestPanel.add(aiStatusLabel);
        form.add(aiTestPanel, gbc);
        aiTestButton.addActionListener(e -> testAiConnection());

        gbc.gridy++;
        JLabel aiHint = new JLabel("Key 存本地 data 目录；测试连接不会保存，请点「保存」");
        aiHint.setForeground(new java.awt.Color(0x6B7280));
        aiHint.setFont(aiHint.getFont().deriveFont(Font.PLAIN, 12f));
        form.add(aiHint, gbc);

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

        AiConfig ai = settings.aiConfig();
        aiEnabledCheck.setSelected(ai.enabled());
        aiKeyField.setText(ai.apiKey());
        aiBaseField.setText(ai.baseUrl());
        aiModelField.setText(ai.model());
    }

    private void save() {
        try {
            AiConfig ai = readAiConfig();
            if (ai.enabled() && ai.apiKey().isBlank()) {
                errorLabel.setText("启用 AI 助手前请填写 API Key");
                return;
            }
            LocalTime start = LocalTime.parse(quietStartField.getText().trim(), TIME);
            LocalTime end = LocalTime.parse(quietEndField.getText().trim(), TIME);
            Settings settings = context.settings();
            settings.setAiConfig(ai);
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

    private AiConfig readAiConfig() {
        return new AiConfig(
                aiEnabledCheck.isSelected(),
                new String(aiKeyField.getPassword()).trim(),
                aiBaseField.getText().trim(),
                aiModelField.getText().trim());
    }

    /** 用当前填写的配置发一条极短请求，验证 Key、地址与模型名。 */
    private void testAiConnection() {
        AiConfig current = readAiConfig();
        if (current.apiKey().isBlank()) {
            setAiStatus("请先填写 API Key", new java.awt.Color(0xC62828));
            return;
        }
        AiConfig test = new AiConfig(true, current.apiKey(), current.baseUrl(), current.model());
        aiTestButton.setEnabled(false);
        setAiStatus("正在连接…", new java.awt.Color(0x6B7280));

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                new DeepSeekClient().testConnection(test);
                return null;
            }

            @Override
            protected void done() {
                aiTestButton.setEnabled(true);
                try {
                    get();
                    setAiStatus("连接成功，点击保存生效", new java.awt.Color(0x2E7D32));
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    String message = cause.getMessage() == null
                            ? cause.getClass().getSimpleName() : cause.getMessage();
                    setAiStatus("连接失败：" + message, new java.awt.Color(0xC62828));
                }
            }
        }.execute();
    }

    /** 状态区只显示一行短文本，完整错误放 tooltip，避免长文本撑坏布局。 */
    private void setAiStatus(String text, java.awt.Color color) {
        String oneLine = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        String display = oneLine.length() <= 46 ? oneLine : oneLine.substring(0, 46) + "…";
        aiStatusLabel.setText(display);
        aiStatusLabel.setToolTipText(oneLine);
        aiStatusLabel.setForeground(color);
        aiStatusLabel.revalidate();
        aiStatusLabel.repaint();
    }
}
