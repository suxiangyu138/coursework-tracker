package com.coursework.tracker.model;

import java.util.Map;

/**
 * 总体完成度统计。
 *
 * @param countRate         条数完成率（0–1）
 * @param weightRate        权重完成率（0–1）
 * @param remainingMinutes  剩余工作量（分钟）
 * @param byCourse          按课程分组的完成度（保持课程列表顺序）
 */
public record CompletionStats(
        int total,
        int completed,
        double countRate,
        double totalWeight,
        double completedWeight,
        double weightRate,
        int remainingMinutes,
        Map<Long, CourseProgress> byCourse) {

    public CompletionStats {
        byCourse = Map.copyOf(byCourse);
    }

    public static CompletionStats empty() {
        return new CompletionStats(0, 0, 0, 0, 0, 0, 0, Map.of());
    }

    public String countRateText() {
        return String.format("%.0f%%", countRate * 100);
    }

    public String weightRateText() {
        return String.format("%.0f%%", weightRate * 100);
    }

    public String remainingText() {
        int minutes = remainingMinutes;
        if (minutes <= 0) {
            return "0 分钟";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours >= 24) {
            return String.format("%d 小时", hours);
        }
        return rest == 0 ? hours + " 小时" : String.format("%d 小时 %d 分", hours, rest);
    }
}
