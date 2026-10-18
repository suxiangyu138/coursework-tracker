package com.coursework.tracker.ui.dialog;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.AppInfo;
import com.coursework.tracker.core.AppPaths;
import com.coursework.tracker.ui.AppIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;

/** 关于 / 作者信息页：产品信息、作者联系方式、运行环境与更新入口。 */
public final class AboutDialog extends JDialog {

    private static final Color ACCENT = new Color(0x2563EB);
    private static final Color MUTED = new Color(0x6B7280);
    private static final Font UI_FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 13);

    private final AppContext context;

    private AboutDialog(Window owner, AppContext context) {
        super(owner, "关于与作者", ModalityType.APPLICATION_MODAL);
        this.context = context;
        buildUi();
        setSize(790, 700);
        setMinimumSize(new Dimension(700, 600));
        setLocationRelativeTo(owner);
        AppIcon.applyTo(this);
    }

    public static void show(Window owner, AppContext context) {
        new AboutDialog(owner, context).setVisible(true);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.add(buildHero(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel buildHero() {
        JPanel hero = new JPanel(new BorderLayout(18, 0)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new LinearGradientPaint(0, 0, getWidth(), getHeight(),
                        new float[]{0f, 1f},
                        new Color[]{new Color(0x312E81), new Color(0x0EA5E9)}));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(255, 255, 255, 22));
                g.fillOval(getWidth() - 180, -90, 300, 300);
                g.fillOval(getWidth() - 330, 60, 180, 180);
                g.dispose();
            }
        };
        hero.setOpaque(false);
        hero.setBorder(BorderFactory.createEmptyBorder(24, 28, 22, 28));

        JLabel icon = new JLabel(new javax.swing.ImageIcon(AppIcon.image(96)));
        icon.setPreferredSize(new Dimension(96, 96));
        hero.add(icon, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(AppInfo.NAME + " · " + AppInfo.ENGLISH_NAME);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 24));
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel tagline = new JLabel("把散落的作业截止日，收进一个常驻桌面的小工具");
        tagline.setForeground(new Color(255, 255, 255, 210));
        tagline.setFont(UI_FONT.deriveFont(13f));
        tagline.setAlignmentX(LEFT_ALIGNMENT);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        meta.setOpaque(false);
        JLabel version = new JLabel("v" + AppInfo.version() + " · 正式版");
        version.setOpaque(true);
        version.setBackground(new Color(255, 255, 255, 46));
        version.setForeground(Color.WHITE);
        version.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        version.setFont(UI_FONT.deriveFont(Font.BOLD, 11f));
        meta.add(version);
        JLabel author = new JLabel("by " + AppInfo.AUTHOR + " · " + AppInfo.AUTHOR_ALIAS);
        author.setForeground(new Color(255, 255, 255, 190));
        author.setFont(UI_FONT.deriveFont(11f));
        meta.add(author);
        meta.setAlignmentX(LEFT_ALIGNMENT);

        text.add(title);
        text.add(Box.createVerticalStrut(6));
        text.add(tagline);
        text.add(Box.createVerticalStrut(12));
        text.add(meta);
        hero.add(text, BorderLayout.CENTER);
        return hero;
    }

    private JScrollPane buildBody() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(18, 22, 8, 22));
        body.setBackground(UIManager.getColor("Panel.background"));

        body.add(buildAuthorCard());
        body.add(Box.createVerticalStrut(14));
        body.add(buildProductCard());
        body.add(Box.createVerticalStrut(14));
        body.add(buildRuntimeCard());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildAuthorCard() {
        JPanel card = createCard("作者");
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);

        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new LinearGradientPaint(0, 0, getWidth(), getHeight(),
                        new float[]{0f, 1f},
                        new Color[]{new Color(0x4F46E5), new Color(0x0EA5E9)}));
                g.fillOval(0, 0, getWidth(), getHeight());
                g.setColor(Color.WHITE);
                g.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 26));
                String text = "苏";
                int width = g.getFontMetrics().stringWidth(text);
                g.drawString(text, (getWidth() - width) / 2, getHeight() / 2 + 9);
                g.dispose();
            }
        };
        avatar.setOpaque(false);
        avatar.setPreferredSize(new Dimension(64, 64));
        avatar.setMaximumSize(new Dimension(64, 64));

        JPanel avatarHolder = new JPanel(new BorderLayout());
        avatarHolder.setOpaque(false);
        avatarHolder.setPreferredSize(new Dimension(64, 64));
        avatarHolder.setMaximumSize(new Dimension(64, Integer.MAX_VALUE));
        avatarHolder.add(avatar, BorderLayout.NORTH);

        JPanel who = new JPanel();
        who.setOpaque(false);
        who.setLayout(new BoxLayout(who, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(AppInfo.AUTHOR + "  ·  " + AppInfo.AUTHOR_ALIAS);
        name.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 18));
        name.setAlignmentX(LEFT_ALIGNMENT);
        JLabel role = new JLabel("独立开发者 · 产品设计 / 开发 / 维护");
        role.setForeground(MUTED);
        role.setFont(UI_FONT.deriveFont(12f));
        role.setAlignmentX(LEFT_ALIGNMENT);
        JTextArea quote = new JTextArea(
                "这是我独立设计与开发的个人产品，会持续迭代。作业哨兵不是一次性的课程作业，"
                        + "而是一个我每天都会用的桌面工具。");
        quote.setEditable(false);
        quote.setOpaque(false);
        quote.setLineWrap(true);
        quote.setWrapStyleWord(true);
        quote.setRows(2);
        quote.setColumns(44);
        quote.setForeground(new Color(0x374151));
        quote.setFont(UI_FONT.deriveFont(Font.ITALIC, 12.5f));
        quote.setBorder(null);
        quote.setFocusable(false);
        quote.setCaretPosition(0);
        quote.setAlignmentX(LEFT_ALIGNMENT);
        who.add(name);
        who.add(Box.createVerticalStrut(4));
        who.add(role);
        who.add(Box.createVerticalStrut(8));
        who.add(quote);

        JPanel contact = new JPanel();
        contact.setOpaque(false);
        contact.setLayout(new BoxLayout(contact, BoxLayout.Y_AXIS));
        contact.add(contactRow("邮箱", link(AppInfo.EMAIL, "mailto:" + AppInfo.EMAIL)));
        contact.add(contactRow("GitHub", link("suxiangyu138 / coursework-tracker  ↗", AppInfo.GITHUB)));
        contact.add(contactRow("Gitee", link("suxiangyu138 / coursework-tracker  ↗", AppInfo.GITEE)));

        row.add(avatarHolder, BorderLayout.WEST);
        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);
        right.add(who, BorderLayout.NORTH);
        right.add(contact, BorderLayout.CENTER);
        row.add(right, BorderLayout.CENTER);
        card.add(row, BorderLayout.CENTER);
        return card;
    }

    private JPanel contactRow(String label, JLabel value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel key = label(label);
        key.setPreferredSize(new Dimension(48, 18));
        row.add(key);
        row.add(value);
        return row;
    }

    private JPanel buildProductCard() {
        JPanel card = createCard("产品");
        JTextArea description = new JTextArea(
                "作业哨兵把课程、作业、截止时间、权重和成绩放在同一个本地数据库里："
                        + "秒级倒计时、紧急度排序、分级提醒、完成度统计、加权成绩与 GPA 预估，"
                        + "还可以用自然语言让 AI 助手帮你建作业、改截止、批量推迟。"
                        + "数据只保存在本机，不登录、不上传。");
        description.setEditable(false);
        description.setOpaque(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setRows(3);
        description.setColumns(58);
        description.setForeground(new Color(0x374151));
        description.setFont(UI_FONT);
        description.setBorder(null);
        description.setFocusable(false);

        JPanel tags = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tags.setOpaque(false);
        tags.add(tag("Java 17"));
        tags.add(tag("Swing + FlatLaf"));
        tags.add(tag("SQLite"));
        tags.add(tag("AI 可选"));
        tags.add(tag("96 项测试"));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(description, BorderLayout.NORTH);
        content.add(tags, BorderLayout.CENTER);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildRuntimeCard() {
        JPanel card = createCard("运行环境");
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(3, 0, 3, 12);
        grid.add(label("版本"), gbc);
        gbc.gridx = 1;
        grid.add(new JLabel("v" + AppInfo.version()), gbc);
        gbc.gridx = 0;
        gbc.gridy++;
        grid.add(label("运行时"), gbc);
        gbc.gridx = 1;
        grid.add(new JLabel(AppInfo.runtime()), gbc);
        gbc.gridx = 0;
        gbc.gridy++;
        grid.add(label("数据目录"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        JTextArea dataPath = new JTextArea(AppPaths.dataDir().toString());
        dataPath.setEditable(false);
        dataPath.setOpaque(false);
        dataPath.setLineWrap(true);
        dataPath.setWrapStyleWord(false);
        dataPath.setFont(UI_FONT.deriveFont(12f));
        dataPath.setForeground(MUTED);
        dataPath.setBorder(null);
        dataPath.setFocusable(false);
        grid.add(dataPath, gbc);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        actions.setOpaque(false);
        JButton openData = new JButton("打开数据目录");
        openData.addActionListener(e -> openPath());
        actions.add(openData);
        gbc.gridx = 1;
        gbc.gridy++;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        grid.add(actions, gbc);

        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(10, 22, 14, 22));

        JLabel copyright = new JLabel("© 2026 " + AppInfo.AUTHOR + " · MIT License · 数据仅保存在本机");
        copyright.setForeground(MUTED);
        copyright.setFont(UI_FONT.deriveFont(11f));
        footer.add(copyright, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton release = new JButton("打开发布页");
        release.addActionListener(e -> openUrl(AppInfo.GITHUB_RELEASES));
        JButton update = new JButton("检查更新");
        update.addActionListener(e -> {
            dispose();
            UpdateDialog.show(getOwner(), context);
        });
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(close);
        buttons.add(release);
        buttons.add(update);
        buttons.add(close);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private JPanel createCard(String title) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(UIManager.getColor("Panel.background"));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB), 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        heading.setForeground(new Color(0x111827));
        card.add(heading, BorderLayout.NORTH);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(UI_FONT.deriveFont(12f));
        return label;
    }

    private JLabel link(String text, String url) {
        JLabel link = new JLabel(text);
        link.setForeground(ACCENT);
        link.setFont(UI_FONT.deriveFont(12.5f));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setToolTipText(url);
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openUrl(url);
            }
        });
        return link;
    }

    private JLabel tag(String text) {
        JLabel tag = new JLabel(text);
        tag.setOpaque(true);
        tag.setBackground(new Color(0xEEF2FF));
        tag.setForeground(new Color(0x3730A3));
        tag.setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
        tag.setFont(UI_FONT.deriveFont(Font.BOLD, 10.5f));
        return tag;
    }

    private void openPath() {
        try {
            Desktop.getDesktop().open(AppPaths.dataDir().toFile());
        } catch (Exception e) {
            copyToClipboard(AppPaths.dataDir().toString());
        }
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // 回退到剪贴板
        }
        copyToClipboard(url);
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }
}
