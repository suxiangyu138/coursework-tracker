package com.coursework.tracker.model;

/**
 * 紧急度评分结果。{@code score} 用于排序；三个分量用于悬浮解释"为什么排在这里"。
 * 已完成作业的分数为 -1（沉底）。
 */
public record Urgency(
        double score,
        double timePressure,
        double weightPressure,
        double effortPressure,
        boolean overdue) {

    public String reason() {
        return String.format("时间压力 %.2f · 权重压力 %.2f · 工作量 %.2f",
                timePressure, weightPressure, effortPressure);
    }

    public String displayScore() {
        return score < 0 ? "—" : String.format("%.2f", score);
    }
}
