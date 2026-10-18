package com.coursework.tracker.ui.table;

import com.coursework.tracker.model.Urgency;
import com.coursework.tracker.ui.ThemeManager;

import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/** 紧急度列：进度条 + 分数，逾期/临期显示红色。 */
public final class UrgencyRenderer extends JProgressBar implements TableCellRenderer {

    public UrgencyRenderer() {
        super(0, 100);
        setStringPainted(true);
        setBorderPainted(false);
        setFont(getFont().deriveFont(Font.PLAIN, 11f));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                   boolean hasFocus, int row, int column) {
        AssignmentTableModel model = (AssignmentTableModel) table.getModel();
        Urgency urgency = model.urgencyAt(table.convertRowIndexToModel(row));
        if (urgency.score() < 0) {
            setValue(0);
            setString("—");
        } else {
            setValue((int) Math.round(Math.min(1.0, urgency.score()) * 100));
            setString(String.format("%.2f", urgency.score()));
        }
        Color color = ThemeManager.urgencyColor(urgency.score());
        setForeground(color);
        setBackground(selected ? table.getSelectionBackground()
                : (row % 2 == 1 ? ThemeManager.alternateRow() : table.getBackground()));
        setOpaque(true);
        return this;
    }
}
