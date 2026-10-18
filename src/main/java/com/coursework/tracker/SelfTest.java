package com.coursework.tracker;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.CompletionStats;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseGrade;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 无界面自检：在临时数据库上生成示例数据，打印统计、成绩与提醒结果，
 * 用于打包后验证 SQLite 原生库、算法与数据库读写是否正常。
 */
public final class SelfTest {

    private SelfTest() {
    }

    public static int run() {
        System.out.println("[selftest] 开始，Java " + System.getProperty("java.version"));
        int created;
        int firstFired;
        int secondFired;
        try {
            Path tempDir = Files.createTempDirectory("coursework-selftest");
            Path dbFile = tempDir.resolve("selftest.db");

            try (AppContext context = AppContext.open(dbFile)) {
                created = context.demoDataFactory().generate(ClockProvider.now());
                List<Course> courses = context.courseService().list();
                List<Assignment> assignments = context.assignmentService().listAll();
                CompletionStats stats = context.statisticsService().compute(courses, assignments);
                List<CourseGrade> grades = context.gradeService().computeAll();
                double gpa = context.gradeService().gpa(grades);

                System.out.printf("[selftest] 课程=%d 作业=%d（生成 %d）%n",
                        courses.size(), assignments.size(), created);
                System.out.printf("[selftest] 完成率=%s 权重完成度=%s 逾期=%d 预计GPA=%.2f%n",
                        stats.countRateText(), stats.weightRateText(),
                        context.statisticsService().overdueCount(assignments, ClockProvider.now()),
                        gpa);

                for (CourseGrade grade : grades) {
                    System.out.printf("[selftest] %-14s 已得=%6.2f 权重=%3.0f%% 均分=%6s 预计=%6s 绩点=%s 需=%s%n",
                            grade.courseName(), grade.earned(), grade.earnedWeight(),
                            format(grade.average()), format(grade.projected()),
                            grade.gradePointText(), grade.neededText());
                }

                firstFired = context.reminderService().tick(ClockProvider.now()).size();
                secondFired = context.reminderService().tick(ClockProvider.now()).size();
                System.out.printf("[selftest] 首次提醒=%d 重复提醒=%d%n", firstFired, secondFired);

                if (courses.size() != 8 || assignments.size() != created) {
                    System.out.println("[selftest] FAIL：示例数据数量不符");
                    return 2;
                }
            }

            try (AppContext reopened = AppContext.open(dbFile)) {
                int count = reopened.assignmentService().listAll().size();
                System.out.println("[selftest] 重新打开数据库，作业数=" + count);
                if (count != created) {
                    System.out.println("[selftest] FAIL：重开后数据不一致");
                    return 3;
                }
            }

            if (firstFired <= 0) {
                System.out.println("[selftest] FAIL：示例数据未触发任何提醒");
                return 4;
            }
            if (secondFired != 0) {
                System.out.println("[selftest] FAIL：提醒出现重复");
                return 5;
            }
            System.out.println("[selftest] PASS");
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }

    private static String format(Double value) {
        return value == null ? "—" : String.format("%.2f", value);
    }
}
