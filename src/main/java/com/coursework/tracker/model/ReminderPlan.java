package com.coursework.tracker.model;

import java.util.List;

/**
 * 一次扫描的提醒规划结果。
 *
 * @param toFire   立即可播报的提醒
 * @param deferred 命中了但处于静音时段、需要延后播报的提醒
 */
public record ReminderPlan(List<PlannedReminder> toFire, List<PlannedReminder> deferred) {

    public ReminderPlan {
        toFire = List.copyOf(toFire);
        deferred = List.copyOf(deferred);
    }

    public static ReminderPlan empty() {
        return new ReminderPlan(List.of(), List.of());
    }

    public boolean isEmpty() {
        return toFire.isEmpty() && deferred.isEmpty();
    }

    public int size() {
        return toFire.size() + deferred.size();
    }
}
