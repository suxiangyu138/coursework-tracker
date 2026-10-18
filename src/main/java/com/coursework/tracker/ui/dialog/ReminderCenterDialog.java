package com.coursework.tracker.ui.dialog;

import com.coursework.tracker.ui.AppIcon;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.model.ReminderHistoryItem;
import com.coursework.tracker.util.Formatters;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;

/** 提醒中心：查看历史提醒，避免托盘气泡被系统折叠后彻底错过。 */
public final class ReminderCenterDialog extends JDialog {

    private static final int LIMIT = 200;

    private final AppContext context;
    private final DefaultTableModel model;
    private final JLabel summary = new JLabel();

    private ReminderCenterDialog(Window owner, AppContext context) {
        super(owner, "提醒中心", ModalityType.MODELESS);
        this.context = context;
        this.model = new DefaultTableModel(
                new Object[]{"时间", "课程", "作业", "提醒类型", "截止时间"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);

        summary.setFont(summary.getFont().deriveFont(Font.PLAIN, 12f));
        summary.setForeground(new java.awt.Color(0x6B7280));

        JButton refresh = new JButton("刷新");
        refresh.addActionListener(e -> reload());
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(close);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
        bottom.add(summary, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(refresh);
        buttons.add(close);
        bottom.add(buttons, BorderLayout.EAST);

        setLayout(new BorderLayout(0, 6));
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setSize(760, 460);
        setLocationRelativeTo(owner);
        AppIcon.applyTo(this);
        reload();
    }

    public static void show(Window owner, AppContext context) {
        new ReminderCenterDialog(owner, context).setVisible(true);
    }

    private void reload() {
        List<ReminderHistoryItem> items = context.reminderLogDao().findRecent(LIMIT);
        model.setRowCount(0);
        for (ReminderHistoryItem item : items) {
            model.addRow(new Object[]{
                    Formatters.display(item.firedAt()),
                    item.courseName() == null ? "未分类" : item.courseName(),
                    item.assignmentTitle(),
                    item.ruleLabel(),
                    Formatters.dueText(item.dueAt())
            });
        }
        summary.setText(items.isEmpty()
                ? "暂无提醒记录：触发过的提醒会保留在这里"
                : "最近 " + items.size() + " 条提醒（最多保留 " + LIMIT + " 条）");
    }
}
