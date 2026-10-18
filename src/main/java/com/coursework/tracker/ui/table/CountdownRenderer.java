package com.coursework.tracker.ui.table;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.Countdown;
import com.coursework.tracker.ui.ThemeManager;
import com.coursework.tracker.util.TimeUtils;

import javax.swing.JTable;
import java.awt.Component;
import java.awt.Font;

/** 倒计时列：按紧迫度着色，已完成显示灰色"已完成"。 */
public final class CountdownRenderer extends BaseRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, selected, hasFocus, row, column);
        AssignmentTableModel model = (AssignmentTableModel) table.getModel();
        Assignment assignment = model.assignmentAt(table.convertRowIndexToModel(row));
        Countdown countdown = TimeUtils.countdown(model.now(), assignment.dueAt());

        setText(assignment.isCompleted() ? "已完成" : countdown.display());
        setForeground(ThemeManager.countdownColor(countdown, assignment.isCompleted()));
        setFont(getFont().deriveFont(assignment.isCompleted() ? Font.PLAIN : Font.BOLD));
        setHorizontalAlignment(CENTER);
        style(this, table, row, selected);
        return this;
    }
}
