package com.coursework.tracker.service;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据：8 门课、20 条作业，包含逾期、临近、已完成与带分数的样本，
 * 便于首次运行与答辩演示。已存在的课程不会重复创建。
 */
public final class DemoDataFactory {

    private final CourseService courseService;
    private final AssignmentService assignmentService;

    public DemoDataFactory(CourseService courseService, AssignmentService assignmentService) {
        this.courseService = courseService;
        this.assignmentService = assignmentService;
    }

    public int generate(LocalDateTime now) {
        Map<String, Course> courses = new HashMap<>();
        for (Course course : courseService.list()) {
            courses.put(course.name(), course);
        }

        Course os = course(courses, "操作系统", 3.0, 90.0);
        Course db = course(courses, "数据库原理", 3.0, 90.0);
        Course network = course(courses, "计算机网络", 3.0, 85.0);
        Course math = course(courses, "概率论与数理统计", 2.0, null);
        Course politics = course(courses, "马克思主义基本原理", 2.0, null);
        Course english = course(courses, "大学英语", 2.0, null);
        Course ds = course(courses, "数据结构", 3.0, null);
        Course se = course(courses, "软件工程", 2.0, null);

        int count = 0;

        // 操作系统：一个已完成的实验 + 一个逾期实验 + 一个临近实验 + 期中项目
        count += add(os, "实验一 进程创建", at(now.minusDays(21), 23, 59), 10, 120,
                AssignmentStatus.DONE, 88.0);
        count += add(os, "实验二 进程调度", at(now.minusDays(2), 23, 59), 10, 180,
                AssignmentStatus.TODO, null);
        count += add(os, "实验三 内存管理", now.plusHours(30), 15, 180, AssignmentStatus.TODO, null);
        count += add(os, "期中项目 模拟器", at(now.plusDays(9), 23, 59), 25, 600,
                AssignmentStatus.DOING, null);

        // 数据库：两次已出分作业 + 一次练习 + 大作业
        count += add(db, "作业一 E-R 设计", at(now.minusDays(14), 18, 0), 10, 90,
                AssignmentStatus.DONE, 92.0);
        count += add(db, "作业二 SQL 查询", at(now.minusDays(7), 18, 0), 10, 120,
                AssignmentStatus.DONE, 85.0);
        count += add(db, "章节练习 事务", at(now.plusDays(2), 18, 0), 5, 90, AssignmentStatus.TODO, null);
        count += add(db, "大作业 图书管理系统", at(now.plusDays(5), 23, 59), 40, 600,
                AssignmentStatus.TODO, null);

        // 计算机网络
        count += add(network, "实验一 抓包分析", at(now.minusDays(10), 23, 59), 10, 120,
                AssignmentStatus.DONE, 90.0);
        count += add(network, "实验二 路由配置", at(now.plusDays(3), 23, 59), 10, 150,
                AssignmentStatus.TODO, null);
        count += add(network, "期末复习提纲", at(now.plusDays(12), 20, 0), 5, 240,
                AssignmentStatus.TODO, null);

        // 概率论：一个明天交、一个 6 小时后截止
        count += add(math, "习题册 第 4 章", at(now.plusDays(1), 20, 0), 10, 90,
                AssignmentStatus.TODO, null);
        count += add(math, "小测 随机变量", now.plusHours(6), 15, 60, AssignmentStatus.TODO, null);

        // 马原：8 小时后截止的读书报告
        count += add(politics, "读书报告", now.plusHours(8), 20, 240, AssignmentStatus.DOING, null);

        // 大学英语
        count += add(english, "听写 Unit 3", at(now.minusDays(5), 10, 0), 10, 30,
                AssignmentStatus.DONE, 90.0);
        count += add(english, "作文 My Campus", at(now.plusDays(4), 23, 59), 10, 60,
                AssignmentStatus.TODO, null);
        count += add(english, "Presentation", at(now.plusDays(14), 10, 0), 15, 120,
                AssignmentStatus.TODO, null);

        // 数据结构
        count += add(ds, "实验 二叉树", at(now.plusDays(4), 23, 59), 10, 120, AssignmentStatus.TODO, null);
        count += add(ds, "课程设计 迷宫求解", at(now.plusDays(11), 23, 59), 30, 900,
                AssignmentStatus.TODO, null);

        // 软件工程
        count += add(se, "需求规格说明书", at(now.plusDays(6), 23, 59), 20, 300,
                AssignmentStatus.TODO, null);

        return count;
    }

    private Course course(Map<String, Course> existing, String name, double credit, Double target) {
        Course course = existing.get(name);
        if (course != null) {
            return course;
        }
        course = courseService.create(name, null, credit, null, target);
        existing.put(name, course);
        return course;
    }

    private int add(Course course, String title, LocalDateTime dueAt, double weight,
                    Integer minutes, AssignmentStatus status, Double score) {
        Assignment draft = Assignment.draft(course.id(), title, dueAt, weight, minutes, null,
                LocalDateTime.now());
        Assignment created = assignmentService.create(draft);
        if (status != AssignmentStatus.TODO) {
            assignmentService.changeStatus(created.id(), status);
        }
        if (score != null) {
            assignmentService.setScore(created.id(), score);
        }
        return 1;
    }

    private static LocalDateTime at(LocalDateTime base, int hour, int minute) {
        return base.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
    }
}
