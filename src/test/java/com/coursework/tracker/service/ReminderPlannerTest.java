package com.coursework.tracker.service;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.FiredKey;
import com.coursework.tracker.model.PlannedReminder;
import com.coursework.tracker.model.ReminderPlan;
import com.coursework.tracker.model.ReminderRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderPlannerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 14, 25);
    private static final Map<Long, String> NAMES = Map.of(1L, "操作系统");
    private static final List<ReminderRule> RULES = List.of(ReminderRule.timedRules());

    private final ReminderPlanner planner = new ReminderPlanner();

    private static Assignment assignment(long id, LocalDateTime dueAt, AssignmentStatus status) {
        return new Assignment(id, 1L, "作业" + id, dueAt, 10, 60, status,
                null, null, null, NOW, NOW);
    }

    private ReminderPlan plan(List<Assignment> assignments, Map<FiredKey, LocalDateTime> fired,
                              boolean quiet) {
        return planner.plan(NOW, assignments, fired, RULES, NAMES, quiet);
    }

    @Test
    @DisplayName("未到触发点不提醒")
    void notYet() {
        assertTrue(plan(List.of(assignment(1, NOW.plusHours(30), AssignmentStatus.TODO)),
                Map.of(), false).isEmpty());
    }

    @Test
    @DisplayName("到达 T-24h 触发一次")
    void firesAtThreshold() {
        ReminderPlan plan = plan(List.of(assignment(1, NOW.plusHours(23), AssignmentStatus.TODO)),
                Map.of(), false);
        assertEquals(1, plan.toFire().size());
        assertEquals(ReminderRule.T24H, plan.toFire().get(0).rule());
        assertTrue(plan.toFire().get(0).message().contains("还有 23 小时"));
    }

    @Test
    @DisplayName("等于触发点也算命中")
    void boundaryExact() {
        ReminderPlan plan = plan(List.of(assignment(1, NOW.plusHours(24), AssignmentStatus.TODO)),
                Map.of(), false);
        assertEquals(1, plan.toFire().size());
        assertEquals(ReminderRule.T24H, plan.toFire().get(0).rule());
    }

    @Test
    @DisplayName("已提醒过的规则不再重复")
    void alreadyFired() {
        Map<FiredKey, LocalDateTime> fired = Map.of(
                FiredKey.of(1L, ReminderRule.T24H), NOW.minusHours(1));
        assertTrue(plan(List.of(assignment(1, NOW.plusHours(23), AssignmentStatus.TODO)),
                fired, false).isEmpty());
    }

    @Test
    @DisplayName("已完成作业不提醒")
    void completedSkips() {
        assertTrue(plan(List.of(assignment(1, NOW.plusHours(2), AssignmentStatus.DONE)),
                Map.of(), false).isEmpty());
    }

    @Test
    @DisplayName("静音时段进入 deferred，不立即播报")
    void quietDefers() {
        ReminderPlan plan = plan(List.of(assignment(1, NOW.plusHours(2), AssignmentStatus.TODO)),
                Map.of(), true);
        assertTrue(plan.toFire().isEmpty());
        assertEquals(1, plan.deferred().size());
    }

    @Test
    @DisplayName("逾期作业进入逾期规则")
    void overdueRule() {
        ReminderPlan plan = plan(List.of(assignment(1, NOW.minusDays(2), AssignmentStatus.TODO)),
                Map.of(), false);
        assertEquals(1, plan.toFire().size());
        assertEquals(ReminderRule.OVERDUE, plan.toFire().get(0).rule());
        assertTrue(plan.toFire().get(0).message().contains("已逾期 2 天"));
    }

    @Test
    @DisplayName("逾期提醒每天一次")
    void overdueOncePerDay() {
        Assignment overdue = assignment(1, NOW.minusDays(2), AssignmentStatus.TODO);
        Map<FiredKey, LocalDateTime> firedToday = Map.of(
                FiredKey.of(1L, ReminderRule.OVERDUE), NOW.minusHours(3));
        assertTrue(plan(List.of(overdue), firedToday, false).isEmpty());

        Map<FiredKey, LocalDateTime> firedYesterday = Map.of(
                FiredKey.of(1L, ReminderRule.OVERDUE), NOW.minusDays(1));
        assertEquals(1, plan(List.of(overdue), firedYesterday, false).toFire().size());
    }

    @Test
    @DisplayName("停机后补发只取最紧急的一档")
    void onlyMostUrgentAfterDowntime() {
        ReminderPlan plan = plan(List.of(assignment(1, NOW.plusMinutes(10), AssignmentStatus.TODO)),
                Map.of(), false);
        assertEquals(1, plan.toFire().size());
        assertEquals(ReminderRule.T15M, plan.toFire().get(0).rule());
    }

    @Test
    @DisplayName("截止瞬间进入逾期规则，不再发未来提醒")
    void atDueTime() {
        ReminderPlan plan = plan(List.of(assignment(1, NOW, AssignmentStatus.TODO)),
                Map.of(), false);
        assertEquals(1, plan.toFire().size());
        assertEquals(ReminderRule.OVERDUE, plan.toFire().get(0).rule());
    }

    @Test
    @DisplayName("多条作业同时命中")
    void multiple() {
        ReminderPlan plan = plan(List.of(
                assignment(1, NOW.plusHours(23), AssignmentStatus.TODO),
                assignment(2, NOW.minusDays(1), AssignmentStatus.TODO)), Map.of(), false);
        assertEquals(2, plan.toFire().size());
    }

    @Test
    @DisplayName("关闭提醒时不产生任何计划")
    void noRules() {
        ReminderPlan plan = planner.plan(NOW,
                List.of(assignment(1, NOW.plusHours(1), AssignmentStatus.TODO)),
                Map.of(), List.of(), NAMES, false);
        assertTrue(plan.isEmpty());
    }

    @Test
    @DisplayName("已提醒更晚档位后不再补发更早档位")
    void noBackfillAfterLaterRuleFired() {
        Map<FiredKey, LocalDateTime> fired = Map.of(
                FiredKey.of(1L, ReminderRule.T6H), NOW);
        assertTrue(plan(List.of(assignment(1, NOW.plusHours(3), AssignmentStatus.TODO)),
                fired, false).isEmpty());
    }

    @Test
    @DisplayName("跨过新档位后继续提醒")
    void nextThresholdStillFires() {
        Map<FiredKey, LocalDateTime> fired = Map.of(
                FiredKey.of(1L, ReminderRule.T24H), NOW.minusHours(20));
        ReminderPlan plan = plan(List.of(assignment(1, NOW.plusHours(5), AssignmentStatus.TODO)),
                fired, false);
        assertEquals(1, plan.toFire().size());
        assertEquals(ReminderRule.T6H, plan.toFire().get(0).rule());
    }

    @Test
    @DisplayName("提醒按触发时间排序")
    void orderedByFireAt() {
        ReminderPlan plan = plan(List.of(
                assignment(1, NOW.plusHours(23), AssignmentStatus.TODO),
                assignment(2, NOW.minusDays(1), AssignmentStatus.TODO)), Map.of(), false);
        List<PlannedReminder> reminders = plan.toFire();
        assertFalse(reminders.get(0).fireAt().isAfter(reminders.get(1).fireAt()));
    }
}
