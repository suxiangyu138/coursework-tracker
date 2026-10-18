package com.coursework.tracker.service;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.CompletionStats;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.DayBucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 14, 25);

    private final StatisticsService service = new StatisticsService();

    private static Course course(long id, String name) {
        return new Course(id, name, null, 3.0, null, null, null, false, NOW);
    }

    private static Assignment assignment(long id, long courseId, LocalDateTime dueAt,
                                         double weight, boolean done) {
        return new Assignment(id, courseId, "作业" + id, dueAt, weight, 60,
                done ? AssignmentStatus.DONE : AssignmentStatus.TODO,
                done ? NOW : null, null, null, NOW, NOW);
    }

    @Test
    @DisplayName("空数据集不除零")
    void empty() {
        CompletionStats stats = service.compute(List.of(course(1, "操作系统")), List.of());
        assertEquals(0, stats.total());
        assertEquals(0.0, stats.countRate(), 0.0001);
        assertEquals(0.0, stats.weightRate(), 0.0001);
    }

    @Test
    @DisplayName("条数完成率与权重完成度差异")
    void countVsWeight() {
        List<Assignment> assignments = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            assignments.add(assignment(i, 1, NOW.plusDays(i), 2, true));
        }
        assignments.add(assignment(99, 1, NOW.plusDays(3), 40, false));

        CompletionStats stats = service.compute(List.of(course(1, "操作系统")), assignments);
        assertEquals(9, stats.total());
        assertEquals(8, stats.completed());
        assertEquals(8.0 / 9.0, stats.countRate(), 0.0001);
        assertEquals(16.0 / 56.0, stats.weightRate(), 0.0001);
    }

    @Test
    @DisplayName("按课程分组统计")
    void byCourse() {
        List<Assignment> assignments = List.of(
                assignment(1, 1, NOW.plusDays(1), 10, true),
                assignment(2, 1, NOW.plusDays(2), 10, false),
                assignment(3, 2, NOW.plusDays(3), 20, true));
        CompletionStats stats = service.compute(
                List.of(course(1, "操作系统"), course(2, "数据库")), assignments);
        assertEquals(2, stats.byCourse().get(1L).total());
        assertEquals(1, stats.byCourse().get(1L).completed());
        assertEquals(1, stats.byCourse().get(2L).total());
        assertEquals(1.0, stats.byCourse().get(2L).weightRate(), 0.0001);
    }

    @Test
    @DisplayName("14 天窗口分桶：今天到第 13 天，第 14 天不计入")
    void workloadBuckets() {
        List<Assignment> assignments = List.of(
                assignment(1, 1, NOW, 10, false),
                assignment(2, 1, NOW.plusDays(1), 10, false),
                assignment(3, 1, NOW.plusDays(13), 10, false),
                assignment(4, 1, NOW.plusDays(14), 10, false),
                assignment(5, 1, NOW.plusDays(2), 10, true));
        List<DayBucket> buckets = service.workload(assignments, NOW, 14);
        assertEquals(14, buckets.size());
        assertEquals(1, buckets.get(0).count());
        assertEquals(1, buckets.get(1).count());
        assertEquals(1, buckets.get(13).count());
        assertEquals(3, buckets.stream().mapToInt(DayBucket::count).sum());
    }

    @Test
    @DisplayName("今日与未来 N 天到期数")
    void dueCounts() {
        List<Assignment> assignments = List.of(
                assignment(1, 1, NOW.plusHours(2), 10, false),
                assignment(2, 1, NOW.plusDays(1), 10, false),
                assignment(3, 1, NOW.minusDays(1), 10, false),
                assignment(4, 1, NOW.plusHours(1), 10, true));
        assertEquals(1, service.dueToday(assignments, NOW.toLocalDate()));
        assertEquals(2, service.dueWithinDays(assignments, NOW, 7));
        assertEquals(1, service.overdueCount(assignments, NOW));
    }

    @Test
    @DisplayName("归档课程不计入统计")
    void archivedCourseExcluded() {
        Assignment archived = assignment(1, 2, NOW, 10, false);
        CompletionStats stats = service.compute(List.of(course(1, "操作系统")), List.of(archived));
        assertEquals(0, stats.total());
        assertTrue(stats.byCourse().containsKey(1L));
    }
}
