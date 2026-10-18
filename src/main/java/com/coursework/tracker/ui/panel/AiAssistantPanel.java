package com.coursework.tracker.ui.panel;

import com.coursework.tracker.ai.AiConfig;
import com.coursework.tracker.ai.AiPromptBuilder;
import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.dao.AiChatDao;
import com.coursework.tracker.model.AiConversation;
import com.coursework.tracker.model.AiMessage;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.service.AdvisorService;
import com.coursework.tracker.ui.PlainText;
import com.coursework.tracker.ui.ThemeManager;
import com.coursework.tracker.ui.dialog.AiChatDialog;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 主界面常驻 AI 助手：本地任务洞察 + 对话 + 一键规划。
 *
 * <p>不打开对话框也能看到当前最该做什么；顶部洞察完全离线计算，
 * 对话与计划生成复用 {@link AiPromptBuilder} 的任务快照和 Agent 工具。
 */
public final class AiAssistantPanel extends JPanel {

    private static final String CONVERSATION_TITLE = "主界面助手";
    private static final String PLAN_PROMPT = """
            请基于当前任务状态，为我制定今天剩余时间的学习计划。
            要求：
            1. 先说明假设的可用时间；我没说时按 19:00–23:00 规划，并注明这是默认假设。
            2. 按紧急度、截止时间、权重排序，不要平均分配。
            3. 每项写清：时间段、课程 · 作业、预计多久、完成标准。
            4. 如果总耗时超过可用时间，明确说明要延后或放弃哪项。
            5. 最后给 3 条执行建议。不要修改任何数据。
            """;
    private static final String RISK_PROMPT = """
            请分析我未来 7 天的任务风险：
            1. 哪些作业最可能逾期，按高/中/低给出风险等级和原因。
            2. 哪些课程权重高但完成度低，对总评影响多大。
            3. 给出 3 条今天就能执行的建议。
            请引用具体课程、作业、截止时间、权重或完成度，不要泛泛而谈；不要修改任何数据。
            """;

    private final Window owner;
    private final AppContext context;
    private final Runnable onCollapse;
    private final Runnable onOpenSettings;
    private final AdvisorService advisor = new AdvisorService();
    private final AiChatDao chatDao;

    private final JTextArea headlineArea = new JTextArea(2, 24);
    private final JTextArea insightArea = new JTextArea(5, 24);
    private final JTextArea transcript = new JTextArea();
    private final JTextArea input = new JTextArea(2, 24);
    private final JLabel modelLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton sendButton = new JButton("发送");
    private final JButton planButton = new JButton("今日计划");
    private final JButton riskButton = new JButton("本周风险");
    private final JButton configButton = new JButton("配置 AI");
    private final JButton fullDialogButton = new JButton("完整对话");
    private final JButton collapseButton = new JButton("收起");

    private JPanel insightCard;
    private JScrollPane transcriptScroll;
    private JScrollPane inputScroll;
    private long conversationId;
    private boolean busy;
    private AdvisorService.TaskBrief brief = AdvisorService.TaskBrief.empty();

    public AiAssistantPanel(Window owner, AppContext context, Runnable onCollapse,
                            Runnable onOpenSettings) {
        this.owner = owner;
        this.context = context;
        this.chatDao = context.aiChatDao();
        this.onCollapse = onCollapse == null ? () -> {
        } : onCollapse;
        this.onOpenSettings = onOpenSettings == null ? () -> {
        } : onOpenSettings;

        setLayout(new BorderLayout(0, 8));
        setOpaque(true);
        setPreferredSize(new Dimension(350, 0));
        applyTheme();

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildInputArea(), BorderLayout.SOUTH);

        refreshStatus();
        ensureConversation(false);
        loadTranscript();
        refresh(List.of(), List.of());
    }

    // ------------------------------------------------------------------
    // 界面
    // ------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        JLabel title = new JLabel("AI 助手");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        JLabel subtitle = new JLabel("常驻分析 · 规划 · 建议");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 10.5f));
        subtitle.setForeground(ThemeManager.mutedText());
        titles.add(title);
        titles.add(subtitle);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        configureSmallButton(fullDialogButton, "打开完整对话窗口，可管理历史会话");
        configureSmallButton(collapseButton, "隐藏 AI 面板，可从工具栏重新打开");
        fullDialogButton.addActionListener(event -> AiChatDialog.show(owner, context));
        collapseButton.addActionListener(event -> onCollapse.run());
        buttons.add(fullDialogButton);
        buttons.add(collapseButton);

        header.add(titles, BorderLayout.WEST);
        header.add(buttons, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(buildInsightCard(), BorderLayout.NORTH);
        body.add(buildTranscript(), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildInsightCard() {
        insightCard = new JPanel(new BorderLayout(0, 4));
        insightCard.setOpaque(true);
        insightCard.setBackground(ThemeManager.alternateRow());
        insightCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 6, 10)));
        insightCard.setPreferredSize(new Dimension(0, 212));

        headlineArea.setEditable(false);
        headlineArea.setOpaque(false);
        headlineArea.setLineWrap(true);
        headlineArea.setWrapStyleWord(true);
        headlineArea.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        headlineArea.setForeground(ThemeManager.isDark() ? Color.WHITE : new Color(0x1F2937));

        insightArea.setEditable(false);
        insightArea.setOpaque(false);
        insightArea.setLineWrap(true);
        insightArea.setWrapStyleWord(true);
        insightArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        insightArea.setForeground(ThemeManager.mutedText());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actions.setOpaque(false);
        configureSmallButton(planButton, "让 AI 根据当前任务生成今天的时间表");
        configureSmallButton(riskButton, "让 AI 分析未来 7 天的逾期与权重风险");
        planButton.addActionListener(event -> sendPrompt(PLAN_PROMPT));
        riskButton.addActionListener(event -> sendPrompt(RISK_PROMPT));
        actions.add(planButton);
        actions.add(riskButton);

        insightCard.add(headlineArea, BorderLayout.NORTH);
        insightCard.add(insightArea, BorderLayout.CENTER);
        insightCard.add(actions, BorderLayout.SOUTH);
        return insightCard;
    }

    private JScrollPane buildTranscript() {
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        transcript.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        transcriptScroll = new JScrollPane(transcript);
        transcriptScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true));
        transcriptScroll.getViewport().setOpaque(false);
        transcriptScroll.setOpaque(false);
        return transcriptScroll;
    }

    private JPanel buildInputArea() {
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setOpaque(false);

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        input.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ai-send");
        input.getActionMap().put("ai-send", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                sendPrompt(input.getText());
            }
        });
        input.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break");

        inputScroll = new JScrollPane(input);
        inputScroll.setPreferredSize(new Dimension(0, 62));
        inputScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true));

        JPanel sendPanel = new JPanel(new BorderLayout(0, 3));
        sendPanel.setOpaque(false);
        sendButton.setFont(sendButton.getFont().deriveFont(Font.BOLD));
        sendButton.setMargin(new java.awt.Insets(4, 10, 4, 10));
        sendButton.addActionListener(event -> sendPrompt(input.getText()));
        JLabel hint = new JLabel("Enter 发送");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 9.5f));
        hint.setForeground(ThemeManager.mutedText());
        sendPanel.add(sendButton, BorderLayout.NORTH);
        sendPanel.add(hint, BorderLayout.SOUTH);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        inputRow.add(inputScroll, BorderLayout.CENTER);
        inputRow.add(sendPanel, BorderLayout.EAST);
        bottom.add(inputRow, BorderLayout.CENTER);

        modelLabel.setFont(modelLabel.getFont().deriveFont(Font.PLAIN, 10f));
        modelLabel.setForeground(ThemeManager.mutedText());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 10f));
        statusLabel.setForeground(ThemeManager.mutedText());
        configureSmallButton(configButton, "打开设置，填写 DeepSeek API Key");
        configButton.addActionListener(event -> onOpenSettings.run());

        JPanel footerTop = new JPanel(new BorderLayout(4, 0));
        footerTop.setOpaque(false);
        footerTop.add(modelLabel, BorderLayout.WEST);
        footerTop.add(configButton, BorderLayout.EAST);
        JPanel footer = new JPanel(new BorderLayout(0, 1));
        footer.setOpaque(false);
        footer.add(footerTop, BorderLayout.NORTH);
        footer.add(statusLabel, BorderLayout.SOUTH);
        bottom.add(footer, BorderLayout.SOUTH);
        return bottom;
    }

    private static void configureSmallButton(JButton button, String tooltip) {
        button.setToolTipText(tooltip);
        button.setMargin(new java.awt.Insets(2, 7, 2, 7));
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 11f));
    }

    // ------------------------------------------------------------------
    // 数据与洞察
    // ------------------------------------------------------------------

    /** 每次主界面刷新时更新本地任务洞察；不发起网络请求。 */
    public void refresh(List<Course> courses, List<Assignment> assignments) {
        LocalDateTime now = ClockProvider.now();
        brief = advisor.analyze(courses, assignments, now,
                context.settings().horizonHours(), context.settings().weights());
        headlineArea.setText(brief.headline());
        headlineArea.setCaretPosition(0);

        StringBuilder text = new StringBuilder();
        for (String insight : brief.insights()) {
            // 「权重压力最大」信息量重要但名字通常很长，放到 AI 提示词里即可；
            // 面板保留四条短洞察，避免小卡片里被截断。
            if (insight.startsWith("权重压力最大")) {
                continue;
            }
            text.append("· ").append(insight).append('\n');
        }
        insightArea.setText(text.toString().trim());
        insightArea.setCaretPosition(0);

        updateControls();
    }

    /** 设置保存后刷新模型/配置状态。 */
    public void refreshStatus() {
        updateControls();
        AiConfig config = context.settings().aiConfig();
        if (config.isConfigured()) {
            statusLabel.setText("就绪 · 会结合当前任务状态回答");
            statusLabel.setForeground(ThemeManager.mutedText());
        } else {
            statusLabel.setText("本地分析可用；配置 API Key 后可对话");
            statusLabel.setForeground(new Color(0xB45309));
        }
    }

    private void updateControls() {
        AiConfig config = context.settings().aiConfig();
        boolean configured = config.isConfigured();
        boolean enabled = configured && !busy;
        input.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        planButton.setEnabled(enabled);
        riskButton.setEnabled(enabled);
        configButton.setVisible(!configured);
        modelLabel.setText(configured ? "模型：" + config.model() : "AI 未配置");
    }

    public void applyTheme() {
        setBackground(ThemeManager.cardBackground());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, ThemeManager.cardBorder()),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        if (insightCard != null) {
            insightCard.setBackground(ThemeManager.alternateRow());
            insightCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true),
                    BorderFactory.createEmptyBorder(8, 10, 6, 10)));
        }
        if (headlineArea != null) {
            headlineArea.setForeground(ThemeManager.isDark() ? Color.WHITE : new Color(0x1F2937));
        }
        if (insightArea != null) {
            insightArea.setForeground(ThemeManager.mutedText());
        }
        if (transcript != null) {
            transcript.setBackground(ThemeManager.cardBackground());
            transcript.setForeground(ThemeManager.isDark() ? new Color(0xE5E7EB) : new Color(0x1F2937));
        }
        if (input != null) {
            input.setBackground(ThemeManager.cardBackground());
            input.setForeground(ThemeManager.isDark() ? new Color(0xE5E7EB) : new Color(0x1F2937));
        }
        if (transcriptScroll != null) {
            transcriptScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true));
        }
        if (inputScroll != null) {
            inputScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true));
        }
        modelLabel.setForeground(ThemeManager.mutedText());
        repaint();
    }

    // ------------------------------------------------------------------
    // 对话
    // ------------------------------------------------------------------

    private void sendPrompt(String rawText) {
        if (busy) {
            return;
        }
        if (!ensureConfigured()) {
            return;
        }
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return;
        }
        if (!ensureConversation(true) || conversationId <= 0) {
            statusLabel.setText("无法创建对话，请重试");
            return;
        }

        LocalDateTime now = ClockProvider.now();
        chatDao.addMessage(AiMessage.user(conversationId, text, now));
        chatDao.touchConversation(conversationId, now);
        append("你", text);
        input.setText("");
        setBusy(true);
        statusLabel.setText("AI 正在分析任务…");
        statusLabel.setForeground(ThemeManager.mutedText());

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return context.aiAgentService().chat(context.settings().aiConfig(), conversationId,
                        AiPromptBuilder.build(context), AiAssistantPanel.this::confirmActions);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    String reply = get();
                    append("AI", PlainText.render(reply));
                    statusLabel.setText("完成");
                    statusLabel.setForeground(new Color(0x2E7D32));
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    String message = cause.getMessage() == null
                            ? cause.getClass().getSimpleName() : cause.getMessage();
                    append("AI", "请求失败：" + message);
                    statusLabel.setText("请求失败");
                    statusLabel.setForeground(new Color(0xC62828));
                }
            }
        }.execute();
    }

    private void setBusy(boolean value) {
        busy = value;
        updateControls();
    }

    private boolean ensureConfigured() {
        if (context.settings().aiConfig().isConfigured()) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "AI 助手尚未配置。\n\n本地任务洞察和优先级分析仍然可用；"
                        + "配置 DeepSeek API Key 后即可对话、生成计划和风险分析。\n\n现在打开设置？",
                "AI 未配置", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            onOpenSettings.run();
        }
        return false;
    }

    private boolean ensureConversation(boolean create) {
        if (conversationId > 0 && chatDao.findConversation(conversationId).isPresent()) {
            return true;
        }
        for (AiConversation conversation : chatDao.listConversations()) {
            if (CONVERSATION_TITLE.equals(conversation.title())) {
                conversationId = conversation.id();
                return true;
            }
        }
        if (!create) {
            conversationId = 0;
            return false;
        }
        conversationId = chatDao.createConversation(CONVERSATION_TITLE, ClockProvider.now());
        return true;
    }

    private void loadTranscript() {
        transcript.setText("");
        if (!ensureConversation(false)) {
            appendWelcome();
            return;
        }
        List<AiMessage> messages = chatDao.findRecentMessages(conversationId, 30);
        boolean any = false;
        for (AiMessage message : messages) {
            if ("user".equals(message.role()) && message.content() != null
                    && !message.content().isBlank()) {
                append("你", message.content());
                any = true;
            } else if ("assistant".equals(message.role()) && message.content() != null
                    && !message.content().isBlank()) {
                append("AI", PlainText.render(message.content()));
                any = true;
            }
        }
        if (!any) {
            appendWelcome();
        }
    }

    private void appendWelcome() {
        append("AI", """
                我是常驻在主界面的 AI 助手。
                我会结合左侧课程和作业状态，帮你判断优先级、制定今天的时间表、分析未来 7 天的风险。
                可以直接提问，也可以点上面的「今日计划」「本周风险」。""");
    }

    private void append(String who, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (transcript.getDocument().getLength() > 50000) {
            transcript.replaceRange("", 0, 15000);
        }
        transcript.append(who + "：\n" + text.trim() + "\n\n");
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }

    /** 写操作确认：Agent 在后台线程回调，切回 EDT 弹窗并等待结果。 */
    private boolean confirmActions(List<String> previews) {
        if (previews == null || previews.isEmpty()) {
            return true;
        }
        final boolean[] approved = {false};
        try {
            SwingUtilities.invokeAndWait(() -> {
                StringBuilder message = new StringBuilder("AI 计划执行以下步骤：\n\n");
                for (int i = 0; i < previews.size(); i++) {
                    message.append(i + 1).append(". ").append(previews.get(i)).append('\n');
                }
                message.append("\n只读查询会一并执行；拒绝则整批都不执行。\n是否执行？");
                Object[] options = {"是", "否"};
                int choice = JOptionPane.showOptionDialog(AiAssistantPanel.this, message.toString(),
                        "AI 执行计划", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                        null, options, options[1]);
                approved[0] = choice == 0;
            });
        } catch (Exception e) {
            approved[0] = false;
        }
        return approved[0];
    }
}
