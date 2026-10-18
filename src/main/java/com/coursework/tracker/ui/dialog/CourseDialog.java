package com.coursework.tracker.ui.dialog;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.Semester;
import com.coursework.tracker.service.BusinessException;
import com.coursework.tracker.ui.AppIcon;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 新建/编辑课程对话框：课程名、学期、学分、教师、目标分。 */
public final class CourseDialog extends JDialog {

    private final AppContext context;
    private final Course existing;

    private final JTextField nameField = new JTextField(18);
    private final JComboBox<TermOption> semesterCombo = new JComboBox<>();
    private final JSpinner creditSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.0, 20.0, 0.5));
    private final JTextField teacherField = new JTextField(18);
    private final JTextField targetField = new JTextField(6);
    private final JLabel errorLabel = new JLabel(" ");

    private Course result;

    public static Course showDialog(Window owner, AppContext context, Course existing) {
        CourseDialog dialog = new CourseDialog(owner, context, existing);
        dialog.setVisible(true);
        return dialog.result;
    }

    private CourseDialog(Window owner, AppContext context, Course existing) {
        super(owner, existing == null ? "新建课程" : "编辑课程", ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.existing = existing;
        loadSemesterOptions();
        buildUi();
        pack();
        setLocationRelativeTo(owner);
        AppIcon.applyTo(this);
    }

    /**
     * 学期下拉框：最近 5 学年 + 数据库里已有的学期（含自定义文本）。
     * 新建课程默认当前学期；编辑老课程默认保留原值（没有则「未分学期」）。
     */
    private void loadSemesterOptions() {
        Set<String> known = new LinkedHashSet<>();
        Semester cursor = Semester.current(ClockProvider.now());
        for (int i = 0; i < 10; i++) {
            known.add(cursor.code());
            cursor = cursor.previous();
        }
        for (Course course : context.courseService().list()) {
            String semester = Semester.normalize(course.semester());
            if (semester != null) {
                known.add(semester);
            }
        }

        List<Semester> parsed = new ArrayList<>();
        List<String> custom = new ArrayList<>();
        for (String code : known) {
            Optional<Semester> semester = Semester.parse(code);
            if (semester.isPresent()) {
                parsed.add(semester.get());
            } else {
                custom.add(code);
            }
        }
        parsed.sort(Comparator.reverseOrder());
        custom.sort(String::compareTo);

        List<TermOption> options = new ArrayList<>();
        for (Semester semester : parsed) {
            options.add(new TermOption(semester.code(), semester.label()));
        }
        for (String code : custom) {
            options.add(new TermOption(code, code));
        }
        options.add(new TermOption("", Semester.UNASSIGNED_LABEL));

        semesterCombo.setModel(new DefaultComboBoxModel<>(options.toArray(new TermOption[0])));
        semesterCombo.setMaximumRowCount(12);
        semesterCombo.setToolTipText("学期用于左侧课程分组；课程名仍需全局唯一");
        selectSemester(existing == null
                ? Semester.current(ClockProvider.now()).code()
                : existing.semester());
    }

    private void selectSemester(String code) {
        String wanted = Semester.normalize(code) == null ? "" : Semester.normalize(code);
        for (int i = 0; i < semesterCombo.getItemCount(); i++) {
            if (semesterCombo.getItemAt(i).code().equals(wanted)) {
                semesterCombo.setSelectedIndex(i);
                return;
            }
        }
        TermOption custom = new TermOption(wanted, wanted);
        semesterCombo.addItem(custom);
        semesterCombo.setSelectedItem(custom);
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 4, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        if (existing != null) {
            nameField.setText(existing.name());
            creditSpinner.setValue(existing.credit());
            teacherField.setText(existing.teacher() == null ? "" : existing.teacher());
            targetField.setText(existing.targetScore() == null ? "" : String.valueOf(existing.targetScore()));
        }

        addRow(form, gbc, 0, "课程名 *", nameField);
        addRow(form, gbc, 1, "学期", semesterCombo);
        addRow(form, gbc, 2, "学分", creditSpinner);
        addRow(form, gbc, 3, "教师", teacherField);
        addRow(form, gbc, 4, "目标分（选填）", targetField);

        errorLabel.setForeground(new Color(0xC62828));
        errorLabel.setFont(errorLabel.getFont().deriveFont(Font.PLAIN, 12f));
        gbc.gridx = 0;
        gbc.gridy = 5;
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

    private void addRow(JPanel form, GridBagConstraints gbc, int row, String label, java.awt.Component field) {
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
            String name = nameField.getText().trim();
            double credit = ((Number) creditSpinner.getValue()).doubleValue();
            String teacher = teacherField.getText().trim();
            Double target = parseTarget(targetField.getText());
            TermOption semesterOption = (TermOption) semesterCombo.getSelectedItem();
            String semester = semesterOption == null ? null : semesterOption.code();

            if (existing == null) {
                result = context.courseService().create(name, null, credit, teacher, semester, target);
            } else {
                Course updated = new Course(existing.id(), name, existing.code(), credit,
                        teacher.isBlank() ? null : teacher, semester, target, existing.archived(),
                        existing.createdAt());
                result = context.courseService().update(updated);
            }
            dispose();
        } catch (BusinessException | IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
        }
    }

    private Double parseTarget(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("目标分应为数字");
        }
    }

    /** 下拉框选项：数据库存 code，界面显示 label。 */
    private record TermOption(String code, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
