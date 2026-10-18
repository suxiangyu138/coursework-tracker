package com.coursework.tracker.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 课程。{@code credit} 用于 GPA 加权；{@code targetScore} 为用户希望达到的总评分（可为空）；
 * {@code semester} 为学期编码（例如 {@code 2026-2027-1}），空表示未分学期。
 */
public record Course(
        long id,
        String name,
        String code,
        double credit,
        String teacher,
        String semester,
        Double targetScore,
        boolean archived,
        LocalDateTime createdAt) {

    public Course {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("课程名不能为空");
        }
        semester = Semester.normalize(semester);
    }

    public static Course draft(String name, String code, double credit, String teacher,
                               String semester, Double targetScore, LocalDateTime now) {
        return new Course(0L, name.trim(), normalize(code), credit, normalize(teacher),
                Semester.normalize(semester), targetScore, false, now);
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public Course withId(long newId) {
        return new Course(newId, name, code, credit, teacher, semester, targetScore, archived, createdAt);
    }

    public Course withArchived(boolean value) {
        return new Course(id, name, code, credit, teacher, semester, targetScore, value, createdAt);
    }

    public Course withSemester(String value) {
        return new Course(id, name, code, credit, teacher, Semester.normalize(value),
                targetScore, archived, createdAt);
    }

    /** 供界面显示：规范编码转中文长标签，未知文本原样显示。 */
    public String semesterLabel() {
        return Semester.display(semester);
    }

    @Override
    public String toString() {
        return name;
    }
}
