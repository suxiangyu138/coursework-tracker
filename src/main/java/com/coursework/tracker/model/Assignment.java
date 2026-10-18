package com.coursework.tracker.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 一次作业/实验/大作业。字段与数据库表 {@code assignment} 一一对应。
 */
public record Assignment(
        long id,
        long courseId,
        String title,
        LocalDateTime dueAt,
        double weight,
        Integer estimatedMinutes,
        AssignmentStatus status,
        LocalDateTime submittedAt,
        Double score,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public Assignment {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(dueAt, "dueAt");
        status = (status == null) ? AssignmentStatus.TODO : status;
        if (weight < 0 || weight > 100) {
            throw new IllegalArgumentException("权重必须在 0–100 之间: " + weight);
        }
        if (score != null && (score < 0 || score > 100)) {
            throw new IllegalArgumentException("得分必须在 0–100 之间: " + score);
        }
        if (estimatedMinutes != null && estimatedMinutes < 0) {
            throw new IllegalArgumentException("预计耗时不能为负: " + estimatedMinutes);
        }
    }

    /** 新建作业草稿：id=0，状态未开始。 */
    public static Assignment draft(long courseId, String title, LocalDateTime dueAt, double weight,
                                   Integer estimatedMinutes, String notes, LocalDateTime now) {
        return new Assignment(0L, courseId, title.trim(), dueAt, weight, estimatedMinutes,
                AssignmentStatus.TODO, null, null, notes, now, now);
    }

    public boolean isCompleted() {
        return status.isCompleted();
    }

    public boolean isOverdue(LocalDateTime now) {
        return !isCompleted() && !dueAt.isAfter(now);
    }

    public int effortMinutesOrDefault(int fallback) {
        return (estimatedMinutes == null || estimatedMinutes <= 0) ? fallback : estimatedMinutes;
    }

    public Assignment withId(long value) {
        return new Assignment(value, courseId, title, dueAt, weight, estimatedMinutes, status,
                submittedAt, score, notes, createdAt, updatedAt);
    }

    public Assignment withCourseId(long value) {
        return new Assignment(id, value, title, dueAt, weight, estimatedMinutes, status,
                submittedAt, score, notes, createdAt, updatedAt);
    }

    public Assignment withTitle(String value) {
        return new Assignment(id, courseId, value, dueAt, weight, estimatedMinutes, status,
                submittedAt, score, notes, createdAt, updatedAt);
    }

    public Assignment withDueAt(LocalDateTime value) {
        return new Assignment(id, courseId, title, value, weight, estimatedMinutes, status,
                submittedAt, score, notes, createdAt, updatedAt);
    }

    public Assignment withWeight(double value) {
        return new Assignment(id, courseId, title, dueAt, value, estimatedMinutes, status,
                submittedAt, score, notes, createdAt, updatedAt);
    }

    public Assignment withEstimatedMinutes(Integer value) {
        return new Assignment(id, courseId, title, dueAt, weight, value, status,
                submittedAt, score, notes, createdAt, updatedAt);
    }

    public Assignment withStatus(AssignmentStatus value) {
        return new Assignment(id, courseId, title, dueAt, weight, estimatedMinutes, value,
                submittedAt, score, notes, createdAt, updatedAt);
    }

    public Assignment withSubmittedAt(LocalDateTime value) {
        return new Assignment(id, courseId, title, dueAt, weight, estimatedMinutes, status,
                value, score, notes, createdAt, updatedAt);
    }

    public Assignment withScore(Double value) {
        return new Assignment(id, courseId, title, dueAt, weight, estimatedMinutes, status,
                submittedAt, value, notes, createdAt, updatedAt);
    }

    public Assignment withNotes(String value) {
        return new Assignment(id, courseId, title, dueAt, weight, estimatedMinutes, status,
                submittedAt, score, value, createdAt, updatedAt);
    }

    public Assignment withUpdatedAt(LocalDateTime value) {
        return new Assignment(id, courseId, title, dueAt, weight, estimatedMinutes, status,
                submittedAt, score, notes, createdAt, value);
    }
}
