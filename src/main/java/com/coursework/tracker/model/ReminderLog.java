package com.coursework.tracker.model;

import java.time.LocalDateTime;

/** 已触发的提醒记录，用于去重与补发判断。 */
public record ReminderLog(long id, long assignmentId, String ruleCode, LocalDateTime firedAt) {

    public static ReminderLog of(long assignmentId, ReminderRule rule, LocalDateTime firedAt) {
        return new ReminderLog(0L, assignmentId, rule.code(), firedAt);
    }

    public boolean matches(ReminderRule rule) {
        return rule.code().equals(ruleCode);
    }
}
