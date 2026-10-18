package com.coursework.tracker.ui.table;

import com.coursework.tracker.ui.ThemeManager;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;

/** 各类单元格渲染器的公共样式：选中/隔行变色。 */
abstract class BaseRenderer extends DefaultTableCellRenderer {

    protected void style(Component component, JTable table, int row, boolean selected) {
        ThemeManager.styleCell((javax.swing.JComponent) component, table, row, selected);
        ((javax.swing.JComponent) component).setOpaque(true);
    }

    protected Color muted() {
        return ThemeManager.mutedText();
    }
}
