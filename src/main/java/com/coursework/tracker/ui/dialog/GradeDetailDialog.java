package com.coursework.tracker.ui.dialog;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseGrade;
import com.coursework.tracker.util.Formatters;
import com.coursework.tracker.util.GradeCalculator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.List;

/** 课程成绩详情：已得加权分、预计总评、目标分反推、绩点与作业构成。 */
public final class GradeDetailDialog extends JDialog {

    private GradeDetailDialog(Window owner, Course course, CourseGrade grade,
                              List<Assignment> assignments) {
        super(owner, "成绩预估 · " + course.name(), ModalityType.APPLICATION_MODAL);

        JPanel summary = new JPanel(new GridLayout(0, 2, 12, 6));
        summary.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));
        addItem(summary, "课程", course.name() + "（" + course.credit() + " 学分）");
        addItem(summary, "已得加权分", String.format("%.2f / 100", grade.earned()));
        addItem(summary, "已完成权重", String.format("%.0f%%", grade.earnedWeight()));
        addItem(summary, "已出分部分均分", grade.average() == null ? "—"
                : String.format("%.2f", grade.average()));
        addItem(summary, "预计总评", grade.projected() == null ? "—" : grade.projectedText());
        addItem(summary, "目标分", course.targetScore() == null ? "未设置"
                : String.format("%.1f", course.targetScore()));
        addItem(summary, "剩余部分所需均分", grade.neededText());
        addItem(summary, "预计绩点", grade.gradePointText());
        if (grade.note() != null) {
            JLabel note = new JLabel("提示：" + grade.note());
            note.setForeground(new Color(0xB26A00));
            note.setFont(note.getFont().deriveFont(Font.PLAIN, 12f));
            summary.add(note);
        }

        String[] columns = {"作业", "权重", "得分", "状态", "截止"};
        Object[][] rows = new Object[assignments.size()][5];
        for (int i = 0; i < assignments.size(); i++) {
            Assignment assignment = assignments.get(i);
            rows[i][0] = assignment.title();
            rows[i][1] = Formatters.weight(assignment.weight());
            rows[i][2] = Formatters.score(assignment.score());
            rows[i][3] = assignment.status().label();
            rows[i][4] = Formatters.dueText(assignment.dueAt());
        }
        DefaultTableModel model = new DefaultTableModel(rows, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);

        JButton close = new JButton("关闭");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        buttons.add(close);
        getRootPane().setDefaultButton(close);

        setLayout(new BorderLayout());
        add(summary, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(640, 520);
        setLocationRelativeTo(owner);
    }

    public static void show(Window owner, AppContext context, Course course) {
        List<Assignment> assignments = context.assignmentService().listByCourse(course.id());
        CourseGrade grade = GradeCalculator.compute(course, assignments, context.gradeService().scale());
        new GradeDetailDialog(owner, course, grade, assignments).setVisible(true);
    }

    private static void addItem(JPanel panel, String label, String value) {
        JLabel name = new JLabel(label);
        name.setForeground(new Color(0x6B7280));
        JLabel content = new JLabel(value == null ? "—" : value);
        content.setFont(content.getFont().deriveFont(Font.BOLD));
        panel.add(name);
        panel.add(content);
    }
}
