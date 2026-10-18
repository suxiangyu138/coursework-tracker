package com.coursework.tracker.model;

import java.time.LocalDateTime;

/**
 * 规划出的一条待提醒（尚未落库），{@code message} 由规划器生成，可直接展示。
 */
public record PlannedReminder(
        Assignment assignment,
        ReminderRule rule,
        LocalDateTime fireAt,
        String message) {

    public FiredKey key() {
        return FiredKey.of(assignment.id(), rule);
    }

    public boolean isOverdue() {
        return rule == ReminderRule.OVERDUE;
    }
}
