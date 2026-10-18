package com.coursework.tracker.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 课程。{@code credit} 用于 GPA 加权；{@code targetScore} 为用户希望达到的总评分（可为空）。
 */
public record Course(
        long id,
        String name,
        String code,
        double credit,
        String teacher,
        Double targetScore,
        boolean archived,
        LocalDateTime createdAt) {

    public Course {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("课程名不能为空");
        }
    }

    public static Course draft(String name, String code, double credit, String teacher,
                               Double targetScore, LocalDateTime now) {
        return new Course(0L, name.trim(), normalize(code), credit, normalize(teacher),
                targetScore, false, now);
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public Course withId(long newId) {
        return new Course(newId, name, code, credit, teacher, targetScore, archived, createdAt);
    }

    public Course withArchived(boolean value) {
        return new Course(id, name, code, credit, teacher, targetScore, value, createdAt);
    }

    @Override
    public String toString() {
        return name;
    }
}
