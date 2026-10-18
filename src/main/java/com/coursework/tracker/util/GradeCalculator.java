package com.coursework.tracker.util;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseGrade;
import com.coursework.tracker.model.GradeScale;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * 成绩计算：加权得分、总评预估、目标分反推、GPA。
 *
 * <p>权重以百分数存储（15 表示 15%），因此
 * {@code earned = Σ(score × weight) / 100}，{@code earnedWeight = Σweight}。
 */
public final class GradeCalculator {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private GradeCalculator() {
    }

    public static CourseGrade compute(Course course, List<Assignment> assignments, GradeScale scale) {
        return compute(course, assignments, scale, null);
    }

    /**
     * @param assumedScore 假设的剩余部分平均分；为 null 时使用已出分部分的加权均分
     */
    public static CourseGrade compute(Course course, List<Assignment> assignments,
                                      GradeScale scale, Double assumedScore) {
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal earnedWeight = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (Assignment assignment : assignments) {
            if (assignment.courseId() != course.id() || assignment.weight() <= 0) {
                continue;
            }
            BigDecimal weight = BigDecimal.valueOf(assignment.weight());
            totalWeight = totalWeight.add(weight, MC);
            if (assignment.score() != null) {
                BigDecimal score = BigDecimal.valueOf(assignment.score());
                earned = earned.add(score.multiply(weight, MC).divide(HUNDRED, MC), MC);
                earnedWeight = earnedWeight.add(weight, MC);
            }
        }

        boolean hasScore = earnedWeight.signum() > 0;
        Double average = null;
        Double projected = null;
        Double neededForTarget = null;
        double maxPossible = 0;
        double gradePoint = 0;
        boolean impossible = false;
        boolean achieved = false;
        String note = null;

        if (!hasScore) {
            note = "暂无成绩";
        } else {
            BigDecimal avg = earned.divide(earnedWeight, MC).multiply(HUNDRED, MC);
            average = avg.doubleValue();

            BigDecimal assumed = (assumedScore != null && assumedScore >= 0 && assumedScore <= 100)
                    ? BigDecimal.valueOf(assumedScore)
                    : avg;
            BigDecimal remainingWeight = HUNDRED.subtract(earnedWeight, MC);
            BigDecimal projectedValue = earned.add(
                    assumed.multiply(remainingWeight, MC).divide(HUNDRED, MC), MC);
            projected = projectedValue.doubleValue();
            gradePoint = scale.toGradePoint(projected);
            maxPossible = earned.add(remainingWeight, MC).doubleValue();

            if (remainingWeight.signum() <= 0) {
                note = "总评已定型";
            } else {
                if (course.targetScore() != null) {
                    BigDecimal target = BigDecimal.valueOf(course.targetScore());
                    BigDecimal required = target.subtract(earned, MC)
                            .divide(remainingWeight, MC)
                            .multiply(HUNDRED, MC);
                    neededForTarget = required.doubleValue();
                    impossible = neededForTarget > 100;
                    achieved = neededForTarget <= 0;
                }
                if (totalWeight.doubleValue() < 99.999) {
                    note = String.format("仅录入 %.0f%% 权重，按 100 分制推算", totalWeight.doubleValue());
                }
            }
        }

        return new CourseGrade(course.id(), course.name(), course.credit(),
                round2(earned), round2(earnedWeight), round2(totalWeight),
                average, projected, neededForTarget, maxPossible, gradePoint,
                hasScore, impossible, achieved, note);
    }

    /** 学分加权 GPA。只统计有成绩且学分大于 0 的课程。 */
    public static double gpa(List<CourseGrade> grades) {
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal points = BigDecimal.ZERO;
        for (CourseGrade grade : grades) {
            if (!grade.hasScore() || grade.credit() <= 0) {
                continue;
            }
            BigDecimal credit = BigDecimal.valueOf(grade.credit());
            credits = credits.add(credit, MC);
            points = points.add(credit.multiply(BigDecimal.valueOf(grade.gradePoint()), MC), MC);
        }
        if (credits.signum() == 0) {
            return 0;
        }
        return points.divide(credits, 3, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
