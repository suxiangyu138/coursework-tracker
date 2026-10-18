package com.coursework.tracker.util;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Urgency;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * 紧急度评分。
 *
 * <pre>
 * timeP   = r &gt;= 0 ? clamp(1 - r / H, 0, 1) : 1 + min(7, ceil(-r/24)) * 0.1
 * weightP = clamp(weight / 40, 0, 1)
 * effortP = clamp(estimatedMinutes / 480, 0, 1)
 * urgency = w1*timeP + w2*weightP + w3*effortP   （进行中 +0.05，已完成 -1）
 * </pre>
 */
public final class UrgencyCalculator {

    public static final double DEFAULT_HORIZON_HOURS = 168.0;
    public static final int DEFAULT_EFFORT_MINUTES = 60;

    private UrgencyCalculator() {
    }

    /** 三个维度的权重，之和必须约等于 1。 */
    public record Weights(double time, double weight, double effort) {

        public static final Weights DEFAULT = new Weights(0.55, 0.30, 0.15);

        public Weights {
            if (time < 0 || weight < 0 || effort < 0) {
                throw new IllegalArgumentException("权重不能为负");
            }
            double sum = time + weight + effort;
            if (Math.abs(sum - 1.0) > 0.001) {
                throw new IllegalArgumentException("三个维度权重之和必须为 1，当前为 " + sum);
            }
        }

        public static Weights of(double time, double weight, double effort) {
            return new Weights(time, weight, effort);
        }
    }

    public static Urgency evaluate(Assignment assignment, LocalDateTime now) {
        return evaluate(assignment, now, DEFAULT_HORIZON_HOURS, Weights.DEFAULT);
    }

    public static Urgency evaluate(Assignment assignment, LocalDateTime now,
                                   double horizonHours, Weights weights) {
        if (assignment.isCompleted()) {
            return new Urgency(-1.0, 0, 0, 0, false);
        }
        double horizon = horizonHours > 0 ? horizonHours : DEFAULT_HORIZON_HOURS;
        double remainingHours = Duration.between(now, assignment.dueAt()).toMinutes() / 60.0;
        boolean overdue = remainingHours <= 0;

        double timePressure;
        if (overdue) {
            double overdueDays = Math.ceil(-remainingHours / 24.0);
            timePressure = 1.0 + Math.min(7.0, Math.max(0.0, overdueDays)) * 0.1;
        } else {
            timePressure = clamp(1.0 - remainingHours / horizon, 0, 1);
        }
        double weightPressure = clamp(assignment.weight() / 40.0, 0, 1);
        double effortPressure = clamp(
                assignment.effortMinutesOrDefault(DEFAULT_EFFORT_MINUTES) / 480.0, 0, 1);

        double score = weights.time() * timePressure
                + weights.weight() * weightPressure
                + weights.effort() * effortPressure;
        if (assignment.status() == AssignmentStatus.DOING) {
            score += 0.05;
        }
        return new Urgency(score, timePressure, weightPressure, effortPressure, overdue);
    }

    /** 默认排序：紧急度降序 → 截止时间升序 → 权重降序 → id。 */
    public static Comparator<Assignment> comparator(LocalDateTime now,
                                                    double horizonHours,
                                                    Weights weights) {
        return Comparator
                .comparingDouble((Assignment a) -> evaluate(a, now, horizonHours, weights).score())
                .reversed()
                .thenComparing(Assignment::dueAt)
                .thenComparing(Comparator.comparingDouble(Assignment::weight).reversed())
                .thenComparingLong(Assignment::id);
    }

    public static Comparator<Assignment> comparator(LocalDateTime now) {
        return comparator(now, DEFAULT_HORIZON_HOURS, Weights.DEFAULT);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
