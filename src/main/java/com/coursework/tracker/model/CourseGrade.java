package com.coursework.tracker.model;

/**
 * 单门课程的成绩视图。
 *
 * <p>{@code earned} 为已得加权分（百分制，例如 38.25/100），{@code earnedWeight} 为已出分权重之和（百分比）。
 * {@code projected} 为总评预估，{@code neededForTarget} 为目标分反推出的"剩余部分所需平均分"。
 */
public record CourseGrade(
        long courseId,
        String courseName,
        double credit,
        double earned,
        double earnedWeight,
        double totalWeight,
        Double average,
        Double projected,
        Double neededForTarget,
        double maxPossible,
        double gradePoint,
        boolean hasScore,
        boolean impossible,
        boolean achieved,
        String note) {

    public String projectedText() {
        return projected == null ? "—" : String.format("%.2f", projected);
    }

    public String gradePointText() {
        return hasScore ? String.format("%.2f", gradePoint) : "—";
    }

    public String neededText() {
        if (neededForTarget == null) {
            return "—";
        }
        if (impossible) {
            return String.format("不可达（最高 %.1f）", maxPossible);
        }
        if (achieved) {
            return "已达标";
        }
        return String.format("%.1f 分", neededForTarget);
    }
}
