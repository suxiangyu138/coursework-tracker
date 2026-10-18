package com.coursework.tracker.service;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.util.UrgencyCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvisorServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 20, 0);

    private static final Course COURSE = new Course(1L, "数据库原理", null, 3.0, "张老师",
            "2026-2027-1", 90.0, false, NOW.minusDays(30));

    private final AdvisorService service = new AdvisorService();

    private static Assignment assignment(long id, String title, LocalDateTime dueAt,
                                         double weight, Integer minutes,
                                         AssignmentStatus status) {
        return new Assignment(id, 1L, title, dueAt, weight, minutes, status,
                status.isCompleted() ? NOW.minusDays(1) : null,
                status.isCompleted() ? 88.0 : null, null, NOW.minusDays(20), NOW);
    }

    private static AdvisorService.TaskBrief brief() {
        List<Assignment> assignments = List.of(
                assignment(1, "大作业 图书管理系统", NOW.minusDays(2), 40, 240, AssignmentStatus.TODO),
                assignment(2, "小测 事务", NOW.plusHours(3), 15, 60, AssignmentStatus.DOING),
                assignment(3, "章节练习", NOW.plusDays(5), 10, 30, AssignmentStatus.TODO),
                assignment(4, "作业一 E-R 设计", NOW.minusDays(10), 20, 60, AssignmentStatus.DONE));
        return new AdvisorService().analyze(List.of(COURSE), assignments, NOW, 168,
                UrgencyCalculator.Weights.DEFAULT);
    }

    @Test
    @DisplayName("识别逾期、今日截止、未来 7 天与剩余工作量")
    void countsAndWorkload() {
        AdvisorService.TaskBrief brief = brief();

        assertEquals(4, brief.total());
        assertEquals(1, brief.completed());
        assertEquals(3, brief.active());
        assertEquals(1, brief.overdue());
        assertEquals(1, brief.dueToday());
        assertEquals(2, brief.dueWithin7Days());
        assertEquals(330, brief.remainingMinutes());
        assertEquals(20.0 / 85.0, brief.weightCompletion(), 0.001);
    }

    @Test
    @DisplayName("逾期任务排最前，标题与建议引用具体课程/作业")
    void focusAndHeadline() {
        AdvisorService.TaskBrief brief = brief();

        assertTrue(brief.headline().contains("已逾期"));
        assertTrue(brief.headline().contains("大作业"));
        assertEquals("大作业 图书管理系统", brief.focus().get(0).title());
        assertEquals("数据库原理", brief.focus().get(0).courseName());
        assertTrue(brief.focus().get(0).reason().contains("已逾期"));
        assertTrue(brief.insights().stream().anyMatch(line -> line.contains("数据库原理")));
        assertTrue(brief.insights().stream().anyMatch(line -> line.contains("大作业")));
        assertTrue(brief.insights().stream().anyMatch(line -> line.contains("权重")));
        assertTrue(service.describe(brief).contains("优先任务"));
    }

    @Test
    @DisplayName("空数据与全部完成时给出可执行提示")
    void emptyAndCompleted() {
        AdvisorService.TaskBrief empty = service.analyze(List.of(), List.of(), NOW, 168,
                UrgencyCalculator.Weights.DEFAULT);
        assertEquals(0, empty.total());
        assertTrue(empty.headline().contains("还没有"));

        AdvisorService.TaskBrief completed = service.analyze(List.of(COURSE),
                List.of(assignment(4, "作业一", NOW.minusDays(1), 20, 60, AssignmentStatus.DONE)),
                NOW, 168, UrgencyCalculator.Weights.DEFAULT);
        assertEquals(0, completed.active());
        assertTrue(completed.headline().contains("完成了"));
        assertTrue(completed.insights().stream().anyMatch(line -> line.contains("复习")));
    }
}
