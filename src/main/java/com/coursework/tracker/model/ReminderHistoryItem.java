package com.coursework.tracker.model;

import java.time.LocalDateTime;

/** 提醒中心的一行历史记录。 */
public record ReminderHistoryItem(
        long id,
        LocalDateTime firedAt,
        String courseName,
        String assignmentTitle,
        String ruleLabel,
        LocalDateTime dueAt) {
}
