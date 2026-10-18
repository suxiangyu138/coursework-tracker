package com.coursework.tracker.model;

/**
 * 单门课程的完成度视图。
 *
 * @param countRate        条数完成率（0–1）
 * @param weightRate       权重完成率（0–1）
 * @param remainingMinutes 剩余工作量（分钟）
 */
public record CourseProgress(
        long courseId,
        String courseName,
        int total,
        int completed,
        double countRate,
        double weightRate,
        int remainingMinutes) {

    public String countRateText() {
        return total == 0 ? "—" : String.format("%.0f%%", countRate * 100);
    }
}
