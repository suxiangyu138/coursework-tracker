package com.coursework.tracker.ui.dialog;

import com.coursework.tracker.ai.AiConfig;
import com.coursework.tracker.ai.AiException;
import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.dao.AiChatDao;
import com.coursework.tracker.model.AiConversation;
import com.coursework.tracker.model.AiMessage;
import com.coursework.tracker.ui.AppIcon;
import com.coursework.tracker.ui.PlainText;
import com.coursework.tracker.util.Formatters;
import com.coursework.tracker.util.TimeUtils;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * AI 助手对话窗口：会话列表、上下文重置、自然语言 Agent。
 * 所有写操作在真正执行前都会弹出确认框。
 */
public final class AiChatDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(AiChatDialog.class.getName());

    private final AppContext context;
    private final AiChatDao chatDao;

    private final DefaultListModel<AiConversation> conversationModel = new DefaultListModel<>();
    private final JList<AiConversation> conversationList = new JList<>(conversationModel);
    private final JLabel titleLabel = new JLabel("新对话");
    private final JLabel contextLabel = new JLabel(" ");
    private final JTextArea transcript = new JTextArea();
    private final JTextArea input = new JTextArea(3, 40);
    private final JButton sendButton = new JButton("发送");
    private final JButton resetButton = new JButton("重置上下文");
    private final JLabel statusLabel = new JLabel(" ");

    private long currentConversationId;
    private boolean updatingList;
    private boolean busy;

    private AiChatDialog(Window owner, AppContext context) {
        super(owner, "AI 助手", ModalityType.MODELESS);
        this.context = context;
        this.chatDao = context.aiChatDao();
        buildUi();
        setSize(1000, 700);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(owner);
        AppIcon.applyTo(this);
        loadConversations(null);
        if (conversationModel.isEmpty()) {
            createConversation();
        } else {
            conversationList.setSelectedIndex(0);
        }
    }

    public static void show(Window owner, AppContext context) {
        new AiChatDialog(owner, context).setVisible(true);
    }

    private void buildUi() {
        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conversationList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                                                                   int index, boolean selected,
                                                                   boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof AiConversation conversation) {
                    setText("<html><b>" + escape(conversation.title()) + "</b><br><small>"
                            + Formatters.display(conversation.updatedAt()) + "</small></html>");
                }
                return this;
            }
        });
        conversationList.addListSelectionListener(event -> {
            if (updatingList || event.getValueIsAdjusting()) {
                return;
            }
            AiConversation selected = conversationList.getSelectedValue();
            if (selected != null) {
                currentConversationId = selected.id();
                titleLabel.setText(selected.title());
                renderMessages();
                updateContextLabel();
            }
        });

        JButton newButton = new JButton("新建对话");
        newButton.addActionListener(e -> createConversation());
        JButton deleteButton = new JButton("删除");
        deleteButton.addActionListener(e -> deleteConversation());

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        leftButtons.add(newButton);
        leftButtons.add(deleteButton);

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
        left.add(new JLabel("对话历史"), BorderLayout.NORTH);
        left.add(new JScrollPane(conversationList), BorderLayout.CENTER);
        left.add(leftButtons, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(230, 0));

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        contextLabel.setForeground(new Color(0x6B7280));
        resetButton.addActionListener(e -> resetContext());
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        header.add(titleLabel, BorderLayout.WEST);
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerRight.add(contextLabel);
        headerRight.add(resetButton);
        header.add(headerRight, BorderLayout.EAST);

        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        transcript.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JScrollPane transcriptScroll = new JScrollPane(transcript);

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send-message");
        input.getActionMap().put("send-message", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
        sendButton.addActionListener(e -> sendMessage());

        JPanel inputPanel = new JPanel(new BorderLayout(8, 6));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 10, 12));
        inputPanel.add(new JScrollPane(input), BorderLayout.CENTER);
        JPanel sendPanel = new JPanel(new BorderLayout(0, 4));
        sendPanel.add(sendButton, BorderLayout.NORTH);
        JLabel hint = new JLabel("Enter 发送");
        hint.setForeground(new Color(0x6B7280));
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        sendPanel.add(hint, BorderLayout.SOUTH);
        inputPanel.add(sendPanel, BorderLayout.EAST);

        statusLabel.setForeground(new Color(0x6B7280));
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout());
        right.add(header, BorderLayout.NORTH);
        right.add(transcriptScroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusPanel, BorderLayout.NORTH);
        bottom.add(inputPanel, BorderLayout.CENTER);
        right.add(bottom, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(230);
        split.setResizeWeight(0);
        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);

        AiConfig config = context.settings().aiConfig();
        if (!config.isConfigured()) {
            statusLabel.setText("AI 未配置：请到「设置 → AI 助手」填写 API Key");
        } else {
            statusLabel.setText("模型：" + config.model() + " · API Key：" + config.maskedKey());
        }
    }

    // ------------------------------------------------------------------
    // 会话管理
    // ------------------------------------------------------------------

    private void loadConversations(Long selectId) {
        updatingList = true;
        conversationModel.clear();
        List<AiConversation> conversations = chatDao.listConversations();
        for (AiConversation conversation : conversations) {
            conversationModel.addElement(conversation);
        }
        if (selectId != null) {
            for (int i = 0; i < conversationModel.size(); i++) {
                if (conversationModel.get(i).id() == selectId) {
                    conversationList.setSelectedIndex(i);
                    break;
                }
            }
        }
        updatingList = false;
    }

    private void createConversation() {
        if (busy) {
            return;
        }
        long id = chatDao.createConversation("新对话", ClockProvider.now());
        loadConversations(id);
        currentConversationId = id;
        titleLabel.setText("新对话");
        transcript.setText("");
        updateContextLabel();
        input.requestFocusInWindow();
    }

    private void deleteConversation() {
        if (busy) {
            return;
        }
        AiConversation selected = conversationList.getSelectedValue();
        if (selected == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "删除对话「" + selected.title() + "」及其全部消息？",
                "删除对话", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        chatDao.deleteConversation(selected.id());
        long previous = currentConversationId;
        loadConversations(null);
        if (conversationModel.isEmpty()) {
            createConversation();
            return;
        }
        for (int i = 0; i < conversationModel.size(); i++) {
            if (conversationModel.get(i).id() != previous) {
                conversationList.setSelectedIndex(i);
                break;
            }
        }
    }

    private void resetContext() {
        if (busy || currentConversationId <= 0) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "清空当前对话的上下文？\n历史消息会被删除，但保留这个对话。",
                "重置上下文", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        chatDao.clearMessages(currentConversationId);
        renderMessages();
        updateContextLabel();
        statusLabel.setText("上下文已重置");
    }

    private void updateContextLabel() {
        if (currentConversationId <= 0) {
            contextLabel.setText(" ");
            return;
        }
        long count = chatDao.countMessages(currentConversationId);
        contextLabel.setText("上下文 " + count + " 条");
    }

    private void renderMessages() {
        transcript.setText("");
        if (currentConversationId <= 0) {
            return;
        }
        for (AiMessage message : chatDao.findAllMessages(currentConversationId)) {
            if ("user".equals(message.role()) && message.content() != null) {
                append("你：\n" + message.content() + "\n\n");
            } else if ("assistant".equals(message.role())
                    && message.content() != null && !message.content().isBlank()) {
                append("AI：\n" + PlainText.render(message.content()) + "\n\n");
            }
        }
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }

    private void append(String text) {
        transcript.append(text);
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }

    // ------------------------------------------------------------------
    // 对话
    // ------------------------------------------------------------------

    private void sendMessage() {
        if (busy) {
            return;
        }
        String text = input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        AiConfig config = context.settings().aiConfig();
        if (!config.isConfigured()) {
            statusLabel.setText("请先到「设置 → AI 助手」填写 API Key 并启用");
            return;
        }
        if (currentConversationId <= 0) {
            createConversation();
        }

        LocalDateTime now = ClockProvider.now();
        AiConversation conversation = chatDao.findConversation(currentConversationId).orElse(null);
        if (conversation != null && "新对话".equals(conversation.title())) {
            chatDao.renameConversation(currentConversationId, truncate(text, 20), now);
            loadConversations(currentConversationId);
        }
        chatDao.addMessage(AiMessage.user(currentConversationId, text, now));

        append("你：\n" + text + "\n\n");
        input.setText("");
        setBusy(true);
        statusLabel.setText("AI 思考中…");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return context.aiAgentService().chat(config, currentConversationId,
                        buildSystemPrompt(), AiChatDialog.this::confirmActions);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    String reply = get();
                    append("AI：\n" + PlainText.render(reply) + "\n\n");
                    statusLabel.setText("完成");
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    append("AI：请求失败 —— " + cause.getMessage() + "\n\n");
                    statusLabel.setText("请求失败");
                }
                loadConversations(currentConversationId);
                AiConversation selected = conversationList.getSelectedValue();
                if (selected != null) {
                    titleLabel.setText(selected.title());
                }
                updateContextLabel();
            }
        }.execute();
    }

    /** 写操作确认：由 Agent 线程回调，切回 EDT 弹窗并等待结果。 */
    private boolean confirmActions(List<String> previews) {
        if (previews == null || previews.isEmpty()) {
            return true;
        }
        LOG.info("AI 写操作待确认：" + previews);
        final boolean[] approved = {false};
        try {
            SwingUtilities.invokeAndWait(() -> {
                StringBuilder message = new StringBuilder("AI 想要执行以下操作：\n\n");
                for (int i = 0; i < previews.size(); i++) {
                    message.append(i + 1).append(". ").append(previews.get(i)).append('\n');
                }
                message.append("\n是否允许执行？");
                Object[] options = {"是", "否"};
                int choice = JOptionPane.showOptionDialog(AiChatDialog.this, message.toString(),
                        "AI 操作确认", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                        null, options, options[1]);
                approved[0] = choice == 0;
            });
        } catch (Exception e) {
            approved[0] = false;
        }
        LOG.info("AI 确认结果：" + approved[0]);
        return approved[0];
    }

    private String buildSystemPrompt() {
        LocalDateTime now = ClockProvider.now();
        int courseCount = context.courseService().list().size();
        int assignmentCount = context.assignmentService().listAll().size();
        return """
                你是「作业哨兵」内置的 AI 助手，帮助大学生管理课程作业与截止时间。
                当前时间：%s %s
                数据概况：%d 门课程、%d 条作业。

                规则：
                1. 用简体中文回答，简洁直接。
                2. 涉及新增、修改、删除数据时，必须调用工具，不要只在文字里描述。
                3. 日期时间格式统一为 yyyy-MM-dd HH:mm。
                4. 不确定课程或作业是否存在时，先调用 list_assignments 查询，必要时用 create_course 新建课程。
                5. 不要编造不存在的课程或作业；工具执行失败时如实说明原因。
                6. 用户拒绝执行后不要再次尝试同样的修改。
                7. 输出纯文本，不要使用 Markdown（不要用 **、#、反引号、代码块、表格）；
                   需要分点时用“1.”“·”和换行。
                """.formatted(Formatters.display(now), TimeUtils.weekday(now), courseCount, assignmentCount);
    }

    private void setBusy(boolean value) {
        busy = value;
        sendButton.setEnabled(!value);
        input.setEnabled(!value);
        conversationList.setEnabled(!value);
        resetButton.setEnabled(!value);
    }

    private static String truncate(String text, int limit) {
        String singleLine = text.replace('\n', ' ').trim();
        return singleLine.length() <= limit ? singleLine : singleLine.substring(0, limit) + "…";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
