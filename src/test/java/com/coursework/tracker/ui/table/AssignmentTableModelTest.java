package com.coursework.tracker.ui.table;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssignmentTableModelTest {

    @Test
    @DisplayName("每秒刷新只通知倒计时列，避免整表重绘")
    void secondTickUpdatesOnlyCountdownColumn() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 18, 15, 0);
        AssignmentTableModel model = new AssignmentTableModel();
        Assignment assignment = new Assignment(1L, 1L, "实验", now.plusHours(3), 20, 60,
                AssignmentStatus.TODO, null, null, null, now, now);
        model.setData(List.of(assignment), Map.of(1L, "操作系统"));

        List<TableModelEvent> events = new ArrayList<>();
        TableModelListener listener = events::add;
        model.addTableModelListener(listener);

        model.setNow(now.plusSeconds(1));

        assertEquals(1, events.size());
        assertEquals(TableModelEvent.UPDATE, events.get(0).getType());
        assertEquals(4, events.get(0).getColumn());
    }
}
