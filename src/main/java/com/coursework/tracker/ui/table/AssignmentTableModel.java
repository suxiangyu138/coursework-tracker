package com.coursework.tracker.ui.table;

import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Urgency;
import com.coursework.tracker.util.UrgencyCalculator;

import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 作业表格模型：负责取值与显示文本，排序交给 TableRowSorter 的比较器。 */
public final class AssignmentTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"状态", "课程", "作业", "截止", "倒计时", "权重", "紧急度"};

    private final List<Assignment> assignments = new ArrayList<>();
    private final Map<Long, String> courseNames = new HashMap<>();

    private LocalDateTime now = ClockProvider.now();
    private double horizonHours = UrgencyCalculator.DEFAULT_HORIZON_HOURS;
    private UrgencyCalculator.Weights weights = UrgencyCalculator.Weights.DEFAULT;

    public void setData(List<Assignment> data, Map<Long, String> names) {
        assignments.clear();
        assignments.addAll(data);
        courseNames.clear();
        courseNames.putAll(names);
        fireTableDataChanged();
    }

    public void setScoring(double horizonHours, UrgencyCalculator.Weights weights) {
        this.horizonHours = horizonHours;
        this.weights = weights;
    }

    public void setNow(LocalDateTime value) {
        this.now = value;
        if (!assignments.isEmpty()) {
            // 只通知"倒计时"列变化，避免每秒整表重绘；紧急度随 60 秒刷新重算
            fireTableChanged(new TableModelEvent(this, 0, assignments.size() - 1, 4,
                    TableModelEvent.UPDATE));
        }
    }

    public LocalDateTime now() {
        return now;
    }

    public Assignment assignmentAt(int row) {
        return assignments.get(row);
    }

    public Urgency urgencyAt(int row) {
        return UrgencyCalculator.evaluate(assignments.get(row), now, horizonHours, weights);
    }

    public List<Assignment> assignments() {
        return List.copyOf(assignments);
    }

    @Override
    public int getRowCount() {
        return assignments.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case 0 -> AssignmentStatus.class;
            case 3, 4 -> LocalDateTime.class;
            case 5, 6 -> Double.class;
            default -> String.class;
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Assignment assignment = assignments.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> assignment.status();
            case 1 -> courseNames.getOrDefault(assignment.courseId(), "未分类");
            case 2 -> assignment.title();
            case 3, 4 -> assignment.dueAt();
            case 5 -> assignment.weight();
            case 6 -> UrgencyCalculator.evaluate(assignment, now, horizonHours, weights).score();
            default -> "";
        };
    }
}
