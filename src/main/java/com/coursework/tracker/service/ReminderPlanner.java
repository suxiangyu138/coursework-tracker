package com.coursework.tracker.service;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.FiredKey;
import com.coursework.tracker.model.PlannedReminder;
import com.coursework.tracker.model.ReminderPlan;
import com.coursework.tracker.model.ReminderRule;
import com.coursework.tracker.util.TimeUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 提醒规划（纯函数，无 IO，便于单元测试）。
 *
 * <p>规则：
 * <ul>
 *   <li>未完成作业才提醒；</li>
 *   <li>未来作业取"已跨过的最晚阈值"（程序停机后启动不会连发四档）；</li>
 *   <li>逾期作业每天最多提醒一次；</li>
 *   <li>静音时段的提醒进入 deferred，不落库、不打扰。</li>
 * </ul>
 */
public final class ReminderPlanner {

    public ReminderPlan plan(LocalDateTime now,
                             List<Assignment> candidates,
                             Map<FiredKey, LocalDateTime> fired,
                             List<ReminderRule> timedRules,
                             Map<Long, String> courseNames,
                             boolean quiet) {
        List<PlannedReminder> immediate = new ArrayList<>();
        List<PlannedReminder> deferred = new ArrayList<>();
        for (Assignment assignment : candidates) {
            if (assignment.isCompleted()) {
                continue;
            }
            PlannedReminder reminder = planOne(now, assignment, fired, timedRules, courseNames);
            if (reminder == null) {
                continue;
            }
            if (quiet) {
                deferred.add(reminder);
            } else {
                immediate.add(reminder);
            }
        }
        Comparator<PlannedReminder> order = Comparator
                .comparing(PlannedReminder::fireAt)
                .thenComparing(r -> r.assignment().dueAt())
                .thenComparing(r -> r.assignment().id());
        immediate.sort(order);
        deferred.sort(order);
        return new ReminderPlan(immediate, deferred);
    }

    private PlannedReminder planOne(LocalDateTime now, Assignment assignment,
                                    Map<FiredKey, LocalDateTime> fired,
                                    List<ReminderRule> timedRules,
                                    Map<Long, String> courseNames) {
        if (assignment.dueAt().isAfter(now)) {
            // 找出所有已经跨过的阈值中"最紧急"的一个（提前量最小）；
            // 已提醒过更晚的档位后，不允许再回头补发更早的档位。
            ReminderRule mostUrgent = null;
            for (ReminderRule rule : timedRules) {
                if (!rule.isTimed()) {
                    continue;
                }
                LocalDateTime fireAt = assignment.dueAt().minus(rule.offset());
                if (now.isBefore(fireAt)) {
                    continue;
                }
                if (mostUrgent == null || rule.offset().compareTo(mostUrgent.offset()) < 0) {
                    mostUrgent = rule;
                }
            }
            if (mostUrgent == null) {
                return null;
            }
            if (fired.containsKey(FiredKey.of(assignment.id(), mostUrgent))) {
                return null;
            }
            return new PlannedReminder(assignment, mostUrgent,
                    assignment.dueAt().minus(mostUrgent.offset()),
                    message(now, assignment, mostUrgent, courseNames));
        }

        LocalDate today = now.toLocalDate();
        LocalDateTime lastOverdue = fired.get(FiredKey.of(assignment.id(), ReminderRule.OVERDUE));
        if (lastOverdue != null && lastOverdue.toLocalDate().equals(today)) {
            return null;
        }
        return new PlannedReminder(assignment, ReminderRule.OVERDUE, assignment.dueAt(),
                message(now, assignment, ReminderRule.OVERDUE, courseNames));
    }

    /** 提醒文案，例如"操作系统 · 实验三 还有 5 小时截止""数据库 · 大作业 已逾期 2 天"。 */
    public static String message(LocalDateTime now, Assignment assignment, ReminderRule rule,
                                 Map<Long, String> courseNames) {
        String course = courseNames.getOrDefault(assignment.courseId(), "");
        String prefix = course.isBlank() ? "" : course + " · ";
        String remaining = TimeUtils.remainingText(now, assignment.dueAt());
        if (rule == ReminderRule.OVERDUE) {
            return prefix + assignment.title() + " " + remaining;
        }
        return prefix + assignment.title() + " 还有 " + remaining + "截止";
    }
}
