package com.coursework.tracker.ui.dialog;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.service.BusinessException;
import com.coursework.tracker.util.Formatters;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/** 新建/编辑作业对话框：课程、标题、截止时间、权重、耗时、状态、得分、备注。 */
public final class AssignmentDialog extends JDialog {

    private final AppContext context;
    private final Assignment existing;

    private final JComboBox<Course> courseCombo;
    private final JTextField titleField = new JTextField(22);
    private final JTextField dueField = new JTextField(16);
    private final JSpinner weightSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 100, 5));
    private final JSpinner effortSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 6000, 15));
    private final JComboBox<AssignmentStatus> statusCombo = new JComboBox<>(AssignmentStatus.values());
    private final JTextField scoreField = new JTextField(6);
    private final JTextArea notesArea = new JTextArea(3, 22);
    private final JLabel errorLabel = new JLabel(" ");

    private Assignment result;

    public static Assignment showDialog(Window owner, AppContext context, Assignment existing) {
        AssignmentDialog dialog = new AssignmentDialog(owner, context, existing);
        dialog.setVisible(true);
        return dialog.result;
    }

    private AssignmentDialog(Window owner, AppContext context, Assignment existing) {
        super(owner, existing == null ? "新建作业" : "编辑作业", ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.existing = existing;
        this.courseCombo = new JComboBox<>(context.courseService().list().toArray(new Course[0]));
        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 4, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 课程选择 + 新建课程
        JPanel coursePanel = new JPanel(new BorderLayout(6, 0));
        coursePanel.setOpaque(false);
        coursePanel.add(courseCombo, BorderLayout.CENTER);
        JButton newCourseButton = new JButton("新建…");
        newCourseButton.setToolTipText("新建一门课程");
        newCourseButton.addActionListener(e -> createCourse());
        coursePanel.add(newCourseButton, BorderLayout.EAST);

        // 截止时间 + 快捷按钮
        JPanel duePanel = new JPanel(new BorderLayout(4, 2));
        duePanel.setOpaque(false);
        duePanel.add(dueField, BorderLayout.NORTH);
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        quick.setOpaque(false);
        quick.add(quickButton("今晚 23:59", () -> setDue(tonight())));
        quick.add(quickButton("明天 23:59", () -> setDue(tomorrow())));
        quick.add(quickButton("下周一", () -> setDue(nextMonday())));
        quick.add(quickButton("+24 小时", () -> {
            LocalDateTime now = ClockProvider.now();
            setDue(now.plusHours(24).withSecond(0).withNano(0));
        }));
        duePanel.add(quick, BorderLayout.CENTER);
        duePanel.add(new JLabel("格式：yyyy-MM-dd HH:mm"), BorderLayout.SOUTH);

        // 耗时 + 单位
        JPanel effortPanel = new JPanel(new BorderLayout(6, 0));
        effortPanel.setOpaque(false);
        effortPanel.add(effortSpinner, BorderLayout.CENTER);
        effortPanel.add(new JLabel("分钟"), BorderLayout.EAST);

        JPanel weightPanel = new JPanel(new BorderLayout(6, 0));
        weightPanel.setOpaque(false);
        weightPanel.add(weightSpinner, BorderLayout.CENTER);
        weightPanel.add(new JLabel("%（占课程总评）"), BorderLayout.EAST);

        int row = 0;
        addRow(form, gbc, row++, "课程 *", coursePanel);
        addRow(form, gbc, row++, "作业标题 *", titleField);
        addRow(form, gbc, row++, "截止时间 *", duePanel);
        addRow(form, gbc, row++, "权重", weightPanel);
        addRow(form, gbc, row++, "预计耗时", effortPanel);
        addRow(form, gbc, row++, "状态", statusCombo);
        addRow(form, gbc, row++, "得分（选填）", scoreField);

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setPreferredSize(new Dimension(260, 70));
        addRow(form, gbc, row++, "备注", notesScroll);

        errorLabel.setForeground(new Color(0xC62828));
        errorLabel.setFont(errorLabel.getFont().deriveFont(Font.PLAIN, 12f));
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        form.add(errorLabel, gbc);

        // 初值
        fillInitialValues();

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

    private void fillInitialValues() {
        if (existing != null) {
            selectCourse(existing.courseId());
            titleField.setText(existing.title());
            dueField.setText(Formatters.display(existing.dueAt()));
            weightSpinner.setValue((int) Math.round(existing.weight()));
            effortSpinner.setValue(existing.estimatedMinutes() == null ? 0 : existing.estimatedMinutes());
            statusCombo.setSelectedItem(existing.status());
            scoreField.setText(existing.score() == null ? "" : String.valueOf(existing.score()));
            notesArea.setText(existing.notes() == null ? "" : existing.notes());
        } else {
            if (courseCombo.getItemCount() > 0) {
                courseCombo.setSelectedIndex(0);
            }
            dueField.setText(Formatters.display(defaultDue()));
            weightSpinner.setValue(10);
            effortSpinner.setValue(60);
            statusCombo.setSelectedItem(AssignmentStatus.TODO);
        }
    }

    private void selectCourse(long courseId) {
        for (int i = 0; i < courseCombo.getItemCount(); i++) {
            if (courseCombo.getItemAt(i).id() == courseId) {
                courseCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void createCourse() {
        Course created = CourseDialog.showDialog(this, context, null);
        if (created != null) {
            courseCombo.addItem(created);
            courseCombo.setSelectedItem(created);
        }
    }

    private JButton quickButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(2, 6, 2, 6));
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 11f));
        button.addActionListener(e -> action.run());
        return button;
    }

    private void setDue(LocalDateTime time) {
        dueField.setText(Formatters.display(time));
    }

    private void addRow(JPanel form, GridBagConstraints gbc, int row, String label,
                        java.awt.Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(field, gbc);
    }

    private void save() {
        try {
            Course course = (Course) courseCombo.getSelectedItem();
            if (course == null) {
                throw new IllegalArgumentException("请先新建一门课程");
            }
            String title = titleField.getText().trim();
            LocalDateTime dueAt = Formatters.parseUserDateTime(dueField.getText());
            double weight = ((Number) weightSpinner.getValue()).doubleValue();
            int minutes = ((Number) effortSpinner.getValue()).intValue();
            Integer estimated = minutes > 0 ? minutes : null;
            AssignmentStatus status = (AssignmentStatus) statusCombo.getSelectedItem();
            Double score = parseScore(scoreField.getText());
            String notes = notesArea.getText().trim();

            LocalDateTime now = ClockProvider.now();
            long id = existing == null ? 0 : existing.id();
            LocalDateTime createdAt = existing == null ? now : existing.createdAt();
            LocalDateTime submittedAt = status.isCompleted()
                    ? (existing != null && existing.submittedAt() != null ? existing.submittedAt() : now)
                    : null;

            Assignment draft = new Assignment(id, course.id(), title, dueAt, weight, estimated,
                    status, submittedAt, score, notes.isBlank() ? null : notes, createdAt, now);

            result = (existing == null)
                    ? context.assignmentService().create(draft)
                    : context.assignmentService().update(draft);
            dispose();
        } catch (BusinessException | IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
        }
    }

    private Double parseScore(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim());
            if (value < 0 || value > 100) {
                throw new IllegalArgumentException("得分应在 0–100 之间");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("得分应为数字");
        }
    }

    private static LocalDateTime tonight() {
        return ClockProvider.now().toLocalDate().atTime(23, 59);
    }

    private static LocalDateTime tomorrow() {
        return ClockProvider.now().toLocalDate().plusDays(1).atTime(23, 59);
    }

    private static LocalDateTime nextMonday() {
        LocalDate today = ClockProvider.now().toLocalDate();
        LocalDate monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return monday.atTime(23, 59);
    }

    private static LocalDateTime defaultDue() {
        LocalDateTime now = ClockProvider.now();
        LocalDate date = now.toLocalTime().isAfter(java.time.LocalTime.of(22, 0))
                ? now.toLocalDate().plusDays(1) : now.toLocalDate();
        return date.atTime(23, 59);
    }
}
