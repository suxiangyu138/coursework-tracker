package com.coursework.tracker.ui.table;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.ui.ThemeManager;
import com.coursework.tracker.util.Formatters;

import javax.swing.JTable;
import java.awt.Component;
import java.awt.Font;
import java.time.LocalDateTime;

/** 截止时间 / 作业标题 / 权重列渲染器。 */
public final class PlainRenderers {

    private PlainRenderers() {
    }

    /** 课程名：普通文本，选中时用选中前景色。 */
    public static final class CourseName extends BaseRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, hasFocus, row, column);
            setText(value == null ? "未分类" : value.toString());
            setFont(getFont().deriveFont(Font.PLAIN));
            setForeground(selected ? table.getSelectionForeground() : table.getForeground());
            style(this, table, row, selected);
            return this;
        }
    }

    /** 截止时间：MM-dd HH:mm 周X。 */
    public static final class DueDate extends BaseRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, hasFocus, row, column);
            LocalDateTime dueAt = (LocalDateTime) value;
            setText(Formatters.dueText(dueAt));
            setForeground(selected ? table.getSelectionForeground() : ThemeManager.mutedText());
            setFont(getFont().deriveFont(Font.PLAIN));
            style(this, table, row, selected);
            return this;
        }
    }

    /** 作业标题：已完成的置灰。 */
    public static final class Title extends BaseRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, hasFocus, row, column);
            AssignmentTableModel model = (AssignmentTableModel) table.getModel();
            Assignment assignment = model.assignmentAt(table.convertRowIndexToModel(row));
            setText(assignment.title());
            setFont(getFont().deriveFont(assignment.isCompleted() ? Font.PLAIN : Font.BOLD));
            if (selected) {
                setForeground(table.getSelectionForeground());
            } else {
                setForeground(assignment.isCompleted()
                        ? ThemeManager.mutedText() : table.getForeground());
            }
            style(this, table, row, selected);
            return this;
        }
    }

    /** 权重：右对齐百分比。 */
    public static final class Weight extends BaseRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, hasFocus, row, column);
            double weight = value == null ? 0 : ((Number) value).doubleValue();
            setText(Formatters.weight(weight));
            setHorizontalAlignment(RIGHT);
            style(this, table, row, selected);
            return this;
        }
    }
}
