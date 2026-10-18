package com.coursework.tracker.util;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseGrade;
import com.coursework.tracker.model.GradeScale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 14, 25);

    private static Course course(double target) {
        return new Course(1L, "数据库原理", null, 3.0, null, target, false, NOW);
    }

    private static Assignment scored(long id, double weight, double score) {
        return new Assignment(id, 1L, "作业" + id, NOW.plusDays(1), weight, null,
                AssignmentStatus.DONE, NOW, score, null, NOW, NOW);
    }

    private static Assignment unscored(long id, double weight) {
        return new Assignment(id, 1L, "作业" + id, NOW.plusDays(10), weight, null,
                AssignmentStatus.TODO, null, null, null, NOW, NOW);
    }

    private static CourseGrade standard() {
        return GradeCalculator.compute(course(90), List.of(
                scored(1, 15, 85), scored(2, 15, 92), scored(3, 15, 78), unscored(4, 55)),
                GradeScale.STANDARD_4);
    }

    @Test
    @DisplayName("三次已出分：earned=38.25、均分 85、预估 85")
    void weightedScore() {
        CourseGrade grade = standard();
        assertEquals(38.25, grade.earned(), 0.001);
        assertEquals(45.0, grade.earnedWeight(), 0.001);
        assertEquals(85.0, grade.average(), 0.001);
        assertEquals(85.0, grade.projected(), 0.001);
        assertEquals(3.7, grade.gradePoint(), 0.001);
        assertNull(grade.note());
    }

    @Test
    @DisplayName("目标 90 分需要剩余部分平均 94.09")
    void neededForTarget() {
        CourseGrade grade = standard();
        assertNotNull(grade.neededForTarget());
        assertEquals(94.09, grade.neededForTarget(), 0.01);
        assertFalse(grade.impossible());
        assertFalse(grade.achieved());
    }

    @Test
    @DisplayName("目标 100 分不可达")
    void impossibleTarget() {
        CourseGrade grade = GradeCalculator.compute(course(100), List.of(
                scored(1, 15, 85), scored(2, 15, 92), scored(3, 15, 78), unscored(4, 55)),
                GradeScale.STANDARD_4);
        assertTrue(grade.impossible());
        assertEquals(93.25, grade.maxPossible(), 0.001);
    }

    @Test
    @DisplayName("已得加权分超过目标时判定达标")
    void achievedTarget() {
        CourseGrade grade = GradeCalculator.compute(course(30), List.of(
                scored(1, 15, 85), scored(2, 15, 92), scored(3, 15, 78), unscored(4, 55)),
                GradeScale.STANDARD_4);
        assertTrue(grade.achieved());
        assertTrue(grade.neededForTarget() <= 0);
    }

    @Test
    @DisplayName("无成绩时不除零")
    void noScore() {
        CourseGrade grade = GradeCalculator.compute(course(90), List.of(unscored(1, 50)),
                GradeScale.STANDARD_4);
        assertFalse(grade.hasScore());
        assertNull(grade.average());
        assertNull(grade.projected());
        assertEquals("暂无成绩", grade.note());
    }

    @Test
    @DisplayName("权重合计不足 100 时给出提示")
    void partialWeights() {
        CourseGrade grade = GradeCalculator.compute(course(90), List.of(
                scored(1, 30, 88), unscored(2, 30)), GradeScale.STANDARD_4);
        assertTrue(grade.note().contains("仅录入 60% 权重"));
    }

    @Test
    @DisplayName("权重已满时总评定型")
    void completedWeights() {
        CourseGrade grade = GradeCalculator.compute(course(90), List.of(scored(1, 100, 85)),
                GradeScale.STANDARD_4);
        assertEquals("总评已定型", grade.note());
        assertEquals(85.0, grade.projected(), 0.001);
    }

    @Test
    @DisplayName("假设分覆盖预测")
    void assumedScoreOverride() {
        CourseGrade grade = GradeCalculator.compute(course(90),
                List.of(scored(1, 30, 88), unscored(2, 70)),
                GradeScale.STANDARD_4, 80.0);
        assertEquals(26.4 + 56.0, grade.projected(), 0.001);
    }

    @Test
    @DisplayName("绩点映射边界")
    void gradePointBoundaries() {
        assertEquals(4.0, GradeScale.STANDARD_4.toGradePoint(90.0), 0.0001);
        assertEquals(3.7, GradeScale.STANDARD_4.toGradePoint(89.99), 0.0001);
        assertEquals(3.0, GradeScale.STANDARD_4.toGradePoint(78.0), 0.0001);
        assertEquals(0.0, GradeScale.STANDARD_4.toGradePoint(59.99), 0.0001);
    }

    @Test
    @DisplayName("学分加权 GPA")
    void gpa() {
        Course first = new Course(1L, "操作系统", null, 3.0, null, null, false, NOW);
        Course second = new Course(2L, "英语", null, 2.0, null, null, false, NOW);
        CourseGrade a = GradeCalculator.compute(first, List.of(scored(1, 100, 85)), GradeScale.STANDARD_4);
        Assignment english = new Assignment(2, 2L, "听写", NOW, 100, null, AssignmentStatus.DONE,
                NOW, 78.0, null, NOW, NOW);
        CourseGrade b = GradeCalculator.compute(second, List.of(english), GradeScale.STANDARD_4);
        assertEquals(3.42, GradeCalculator.gpa(List.of(a, b)), 0.001);
    }

    @Test
    @DisplayName("无学分课程不参与 GPA 分母")
    void gpaSkipsZeroCredit() {
        Course zero = new Course(3L, "讲座", null, 0, null, null, false, NOW);
        CourseGrade grade = GradeCalculator.compute(zero, List.of(scored(9, 100, 95)),
                GradeScale.STANDARD_4);
        assertEquals(0.0, GradeCalculator.gpa(List.of(grade)), 0.0001);
    }
}
