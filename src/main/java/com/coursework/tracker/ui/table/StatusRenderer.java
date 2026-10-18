package com.coursework.tracker.ui.table;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.ui.ThemeManager;

import javax.swing.JTable;
import java.awt.Component;
import java.awt.Font;

/** 状态列：符号 + 中文，按状态着色。 */
public final class StatusRenderer extends BaseRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, selected, hasFocus, row, column);
        AssignmentTableModel model = (AssignmentTableModel) table.getModel();
        Assignment assignment = model.assignmentAt(table.convertRowIndexToModel(row));
        setText(assignment.status().symbol() + " " + assignment.status().label());
        setForeground(ThemeManager.statusColor(assignment.status()));
        setFont(getFont().deriveFont(Font.PLAIN));
        setHorizontalAlignment(LEFT);
        style(this, table, row, selected);
        return this;
    }
}
