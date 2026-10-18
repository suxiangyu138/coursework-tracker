package com.coursework.tracker.ui.dialog;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.AppInfo;
import com.coursework.tracker.core.AppPaths;
import com.coursework.tracker.service.UpdateService;
import com.coursework.tracker.ui.AppIcon;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * 安全更新窗口：选择新版本 jar → 备份数据库与旧版程序 → 退出并由脚本替换 jar → 自动重启。
 * 更新过程完全不触碰 data/ 目录，作业、成绩、API Key 都会保留。
 */
public final class UpdateDialog extends JDialog {

    private static final Font UI_FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
    private static final Color MUTED = new Color(0x6B7280);

    private final AppContext context;
    private final UpdateService updateService;
    private final JTextField fileField = new JTextField();
    private final JTextArea statusArea = new JTextArea(2, 40);
    private final JButton chooseButton = new JButton("选择新版本 jar…");
    private final JButton applyButton = new JButton("立即更新并重启");
    private final JLabel selectedLabel = new JLabel("尚未选择更新包");

    private UpdateService.PackageInfo selected;

    private UpdateDialog(Window owner, AppContext context) {
        super(owner, "检查更新", ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.updateService = new UpdateService(context.backupService());
        buildUi();
        setSize(700, 500);
        setMinimumSize(new Dimension(620, 460));
        setLocationRelativeTo(owner);
        AppIcon.applyTo(this);
        refreshState();
    }

    public static void show(Window owner, AppContext context) {
        new UpdateDialog(owner, context).setVisible(true);
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 10, 20));
        JLabel title = new JLabel("安全更新");
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 20));
        JLabel version = new JLabel("当前版本 v" + AppInfo.version());
        version.setForeground(MUTED);
        version.setFont(UI_FONT);
        header.add(title, BorderLayout.WEST);
        header.add(version, BorderLayout.EAST);

        JTextArea intro = new JTextArea(
                "更新只会替换程序 jar，不会碰 data/ 目录：作业、成绩、备份和 API Key 都会保留。"
                        + "更新前会自动备份数据库，旧版 jar 也会保留为 update-backup.jar。"
                        + "由于仓库是私有的，请先从发布页下载新版本 jar，再在下面选择。");
        intro.setEditable(false);
        intro.setLineWrap(true);
        intro.setWrapStyleWord(true);
        intro.setOpaque(false);
        intro.setFont(UI_FONT);
        intro.setForeground(new Color(0x374151));
        intro.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        intro.setFocusable(false);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 0, 4, 10);
        form.add(label("当前程序"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextArea currentJar = pathArea(updateService.currentJar() == null
                ? "开发模式：请运行 dist/coursework-tracker.jar 后再更新"
                : updateService.currentJar().toString());
        form.add(currentJar, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(label("数据目录"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(pathArea(AppPaths.dataDir().toString()), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(label("更新包"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel filePanel = new JPanel(new BorderLayout(8, 0));
        fileField.setEditable(false);
        filePanel.add(fileField, BorderLayout.CENTER);
        filePanel.add(chooseButton, BorderLayout.EAST);
        form.add(filePanel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        selectedLabel.setForeground(MUTED);
        selectedLabel.setFont(UI_FONT.deriveFont(12f));
        form.add(selectedLabel, gbc);

        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setOpaque(false);
        statusArea.setFont(UI_FONT.deriveFont(12f));
        statusArea.setForeground(new Color(0xB91C1C));
        statusArea.setBorder(null);
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setBorder(null);
        statusScroll.setPreferredSize(new Dimension(600, 46));
        statusScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        JPanel body = new JPanel();
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(12, 20, 4, 20));
        intro.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        int formHeight = form.getPreferredSize().height;
        form.setAlignmentX(0f);
        form.setPreferredSize(new Dimension(620, formHeight));
        form.setMaximumSize(new Dimension(620, formHeight));
        statusScroll.setAlignmentX(LEFT_ALIGNMENT);
        body.add(intro);
        body.add(form);
        body.add(javax.swing.Box.createVerticalStrut(8));
        body.add(statusScroll);
        body.add(javax.swing.Box.createVerticalGlue());

        JButton release = new JButton("打开发布页");
        release.addActionListener(e -> openUrl(AppInfo.GITHUB_RELEASES));
        JButton cancel = new JButton("取消");
        cancel.addActionListener(e -> dispose());
        applyButton.setEnabled(false);
        applyButton.addActionListener(e -> applyUpdate());
        chooseButton.addActionListener(e -> choosePackage());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 20, 8, 20));
        buttons.add(release);
        buttons.add(cancel);
        buttons.add(applyButton);

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(cancel);
    }

    private void refreshState() {
        boolean supported = updateService.isSupported();
        chooseButton.setEnabled(supported);
        if (!supported) {
            setStatus("当前环境不支持一键更新：请运行 dist 下的 jar，并确认程序目录可写。", true);
            return;
        }
        setStatus("选择新版本 jar 后，会先备份数据库，再关闭程序完成替换。", false);
    }

    private void choosePackage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择新版本 jar");
        chooser.setFileFilter(new FileNameExtensionFilter("作业哨兵 jar (*.jar)", "jar"));
        File downloads = new File(System.getProperty("user.home", "."), "Downloads");
        if (downloads.isDirectory()) {
            chooser.setCurrentDirectory(downloads);
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        try {
            selected = updateService.inspect(file);
            fileField.setText(selected.file().toString());
            fileField.setToolTipText(selected.file().toString());
            selectedLabel.setText("已选择 v" + selected.version() + " · "
                    + Math.max(1, selected.size() / 1024 / 1024) + " MB");
            setStatus("更新包校验通过，可以更新。", false);
            applyButton.setEnabled(true);
        } catch (IOException e) {
            selected = null;
            fileField.setText(file.toString());
            selectedLabel.setText("未通过校验");
            setStatus(e.getMessage(), true);
            applyButton.setEnabled(false);
        }
    }

    private void applyUpdate() {
        if (selected == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "将把当前程序更新到 v" + selected.version() + "。\n"
                        + "更新会关闭程序并自动重启，data 数据不会删除。\n\n是否继续？",
                "确认更新", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        chooseButton.setEnabled(false);
        applyButton.setEnabled(false);
        setStatus("正在备份数据库并准备更新包…", false);

        new SwingWorker<UpdateService.UpdatePlan, Void>() {
            @Override
            protected UpdateService.UpdatePlan doInBackground() throws Exception {
                return updateService.prepare(selected);
            }

            @Override
            protected void done() {
                try {
                    UpdateService.UpdatePlan plan = get();
                    int confirm = JOptionPane.showConfirmDialog(UpdateDialog.this,
                            "数据库已备份到：\n" + plan.databaseBackup() + "\n\n"
                                    + "程序即将关闭，替换 jar 后自动重启。\n现在开始吗？",
                            "更新就绪", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                    if (confirm != JOptionPane.YES_OPTION) {
                        setStatus("已取消，未替换程序。", false);
                        resetButtons();
                        return;
                    }
                    updateService.launch(plan);
                    dispose();
                    System.exit(0);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    setStatus("更新失败：" + cause.getMessage(), true);
                    resetButtons();
                }
            }
        }.execute();
    }

    private void resetButtons() {
        chooseButton.setEnabled(updateService.isSupported());
        applyButton.setEnabled(selected != null);
    }

    private void setStatus(String text, boolean error) {
        statusArea.setText(text == null ? "" : text);
        statusArea.setForeground(error ? new Color(0xB91C1C) : new Color(0x2E7D32));
    }

    private JTextArea pathArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(false);
        area.setFont(UI_FONT.deriveFont(11.5f));
        area.setForeground(MUTED);
        area.setBorder(null);
        area.setRows(1);
        return area;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(UI_FONT.deriveFont(12f));
        return label;
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // 忽略，改为提示
        }
        JOptionPane.showMessageDialog(this, url, "发布页", JOptionPane.INFORMATION_MESSAGE);
    }
}
