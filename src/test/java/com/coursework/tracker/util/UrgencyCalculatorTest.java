package com.coursework.tracker.util;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrgencyCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 14, 25);

    private static Assignment assignment(long id, double weight, LocalDateTime dueAt,
                                         Integer minutes, AssignmentStatus status) {
        return new Assignment(id, 1L, "作业" + id, dueAt, weight, minutes, status,
                null, null, null, NOW, NOW);
    }

    private static Urgency evaluate(Assignment assignment) {
        return UrgencyCalculator.evaluate(assignment, NOW);
    }

    @Test
    @DisplayName("样例 A：权重 15、剩 30 小时、180 分钟")
    void sampleA() {
        Urgency urgency = evaluate(assignment(1, 15, NOW.plusHours(30), 180, AssignmentStatus.TODO));
        assertEquals(0.6205, urgency.score(), 0.001);
    }

    @Test
    @DisplayName("样例 B：权重 40、剩 120 小时、600 分钟")
    void sampleB() {
        Urgency urgency = evaluate(assignment(2, 40, NOW.plusHours(120), 600, AssignmentStatus.TODO));
        assertEquals(0.6071, urgency.score(), 0.001);
    }

    @Test
    @DisplayName("样例 C：权重 5、剩 6 小时、120 分钟")
    void sampleC() {
        Urgency urgency = evaluate(assignment(3, 5, NOW.plusHours(6), 120, AssignmentStatus.TODO));
        assertEquals(0.6054, urgency.score(), 0.001);
    }

    @Test
    @DisplayName("样例 D：逾期两天，分数最高")
    void sampleD() {
        Urgency urgency = evaluate(assignment(4, 30, NOW.minusDays(2), 240, AssignmentStatus.TODO));
        assertEquals(0.96, urgency.score(), 0.001);
        assertTrue(urgency.overdue());
    }

    @Test
    @DisplayName("逾期时间压力封顶 1.7")
    void overdueCapped() {
        Urgency urgency = evaluate(assignment(5, 0, NOW.minusDays(30), 0, AssignmentStatus.TODO));
        assertEquals(1.7, urgency.timePressure(), 0.0001);
    }

    @Test
    @DisplayName("权重压力在 40% 处封顶")
    void weightPressureCapped() {
        assertEquals(1.0, evaluate(assignment(6, 40, NOW.plusHours(10), 0, AssignmentStatus.TODO))
                .weightPressure(), 0.0001);
        assertEquals(1.0, evaluate(assignment(7, 100, NOW.plusHours(10), 0, AssignmentStatus.TODO))
                .weightPressure(), 0.0001);
    }

    @Test
    @DisplayName("预计耗时缺省按 60 分钟计算")
    void effortDefault() {
        Urgency urgency = evaluate(assignment(8, 0, NOW.plusHours(1000), null, AssignmentStatus.TODO));
        assertEquals(60.0 / 480.0, urgency.effortPressure(), 0.0001);
    }

    @Test
    @DisplayName("已完成作业沉底（-1）")
    void completedSinks() {
        assertEquals(-1.0, evaluate(assignment(9, 50, NOW.plusHours(2), 100,
                AssignmentStatus.DONE)).score(), 0.0001);
        assertEquals(-1.0, evaluate(assignment(10, 50, NOW.plusHours(2), 100,
                AssignmentStatus.SUBMITTED)).score(), 0.0001);
    }

    @Test
    @DisplayName("进行中作业 +0.05")
    void doingBonus() {
        double todo = evaluate(assignment(11, 20, NOW.plusHours(48), 120,
                AssignmentStatus.TODO)).score();
        double doing = evaluate(assignment(12, 20, NOW.plusHours(48), 120,
                AssignmentStatus.DOING)).score();
        assertEquals(0.05, doing - todo, 0.0001);
    }

    @Test
    @DisplayName("远未来作业时间压力为 0")
    void farFuture() {
        Urgency urgency = evaluate(assignment(13, 10, NOW.plusDays(30), 60, AssignmentStatus.TODO));
        assertEquals(0.0, urgency.timePressure(), 0.0001);
    }

    @Test
    @DisplayName("视野窗口可配置")
    void customHorizon() {
        Urgency urgency = UrgencyCalculator.evaluate(
                assignment(14, 0, NOW.plusHours(12), 0, AssignmentStatus.TODO),
                NOW, 24, UrgencyCalculator.Weights.DEFAULT);
        assertEquals(0.5, urgency.timePressure(), 0.0001);
    }

    @Test
    @DisplayName("排序：逾期 > 临近 > 权重高 > 权重低")
    void ordering() {
        List<Assignment> list = new ArrayList<>(List.of(
                assignment(1, 15, NOW.plusHours(30), 180, AssignmentStatus.TODO),
                assignment(2, 40, NOW.plusHours(120), 600, AssignmentStatus.TODO),
                assignment(3, 5, NOW.plusHours(6), 120, AssignmentStatus.TODO),
                assignment(4, 30, NOW.minusDays(2), 240, AssignmentStatus.TODO),
                assignment(5, 10, NOW.plusHours(120), 60, AssignmentStatus.TODO)));
        list.sort(UrgencyCalculator.comparator(NOW));
        assertEquals(List.of(4L, 1L, 2L, 3L, 5L), list.stream().map(Assignment::id).toList());
    }

    @Test
    @DisplayName("同分时截止时间早的在前")
    void tieBreaksByDueDate() {
        Assignment later = assignment(1, 40, NOW.plusDays(20), 480, AssignmentStatus.TODO);
        Assignment earlier = assignment(2, 40, NOW.plusDays(10), 480, AssignmentStatus.TODO);
        List<Assignment> list = new ArrayList<>(List.of(later, earlier));
        list.sort(UrgencyCalculator.comparator(NOW));
        assertEquals(2L, list.get(0).id());
    }

    @Test
    @DisplayName("权重之和不为 1 时拒绝")
    void invalidWeights() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> UrgencyCalculator.Weights.of(0.5, 0.3, 0.3));
    }
}
