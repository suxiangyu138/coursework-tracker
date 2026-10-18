package com.coursework.tracker.service;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.CompletionStats;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseProgress;
import com.coursework.tracker.model.DayBucket;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 完成度与工作量统计（纯计算，不碰数据库）。 */
public final class StatisticsService {

    public CompletionStats compute(List<Course> courses, List<Assignment> assignments) {
        Map<Long, Course> courseById = new LinkedHashMap<>();
        for (Course course : courses) {
            courseById.put(course.id(), course);
        }

        Map<Long, Counter> counters = new LinkedHashMap<>();
        for (Course course : courses) {
            counters.put(course.id(), new Counter());
        }

        int total = 0;
        int completed = 0;
        double totalWeight = 0;
        double completedWeight = 0;
        int remainingMinutes = 0;

        for (Assignment assignment : assignments) {
            Counter counter = counters.get(assignment.courseId());
            if (counter == null) {
                continue; // 已归档或未知课程
            }
            boolean done = assignment.isCompleted();
            total++;
            counter.add(assignment, done);
            if (done) {
                completed++;
            }
            if (assignment.weight() > 0) {
                totalWeight += assignment.weight();
                if (done) {
                    completedWeight += assignment.weight();
                }
            }
            if (!done && assignment.estimatedMinutes() != null) {
                remainingMinutes += assignment.estimatedMinutes();
            }
        }

        Map<Long, CourseProgress> byCourse = new LinkedHashMap<>();
        for (Course course : courses) {
            Counter counter = counters.get(course.id());
            byCourse.put(course.id(), counter.toProgress(course));
        }

        return new CompletionStats(total, completed,
                total == 0 ? 0 : (double) completed / total,
                totalWeight, completedWeight,
                totalWeight <= 0 ? 0 : completedWeight / totalWeight,
                remainingMinutes, byCourse);
    }

    /** 未来 N 天按天聚合的待完成工作量（图表数据）。 */
    public List<DayBucket> workload(List<Assignment> assignments, LocalDateTime now, int days) {
        LocalDate today = now.toLocalDate();
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        Map<LocalDate, Double> weights = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = today.plusDays(i);
            counts.put(day, 0);
            weights.put(day, 0.0);
        }
        for (Assignment assignment : assignments) {
            if (assignment.isCompleted()) {
                continue;
            }
            LocalDate dueDate = assignment.dueAt().toLocalDate();
            if (!counts.containsKey(dueDate)) {
                continue;
            }
            counts.merge(dueDate, 1, Integer::sum);
            weights.merge(dueDate, assignment.weight(), Double::sum);
        }
        List<DayBucket> buckets = new ArrayList<>(days);
        for (Map.Entry<LocalDate, Integer> entry : counts.entrySet()) {
            buckets.add(new DayBucket(entry.getKey(), entry.getValue(),
                    weights.getOrDefault(entry.getKey(), 0.0)));
        }
        return buckets;
    }

    /** 今天到期的未完成作业数。 */
    public int dueToday(List<Assignment> assignments, LocalDate today) {
        int count = 0;
        for (Assignment assignment : assignments) {
            if (!assignment.isCompleted() && assignment.dueAt().toLocalDate().equals(today)) {
                count++;
            }
        }
        return count;
    }

    /** 从今天起 N 天内到期的未完成作业数。 */
    public int dueWithinDays(List<Assignment> assignments, LocalDateTime now, int days) {
        LocalDateTime end = now.plusDays(days);
        int count = 0;
        for (Assignment assignment : assignments) {
            if (!assignment.isCompleted()
                    && !assignment.dueAt().isBefore(now)
                    && assignment.dueAt().isBefore(end)) {
                count++;
            }
        }
        return count;
    }

    /** 逾期未完成的数量。 */
    public int overdueCount(List<Assignment> assignments, LocalDateTime now) {
        int count = 0;
        for (Assignment assignment : assignments) {
            if (assignment.isOverdue(now)) {
                count++;
            }
        }
        return count;
    }

    private static final class Counter {
        private int total;
        private int completed;
        private double totalWeight;
        private double completedWeight;
        private int remainingMinutes;

        void add(Assignment assignment, boolean done) {
            total++;
            if (done) {
                completed++;
            }
            if (assignment.weight() > 0) {
                totalWeight += assignment.weight();
                if (done) {
                    completedWeight += assignment.weight();
                }
            }
            if (!done && assignment.estimatedMinutes() != null) {
                remainingMinutes += assignment.estimatedMinutes();
            }
        }

        CourseProgress toProgress(Course course) {
            return new CourseProgress(course.id(), course.name(), total, completed,
                    total == 0 ? 0 : (double) completed / total,
                    totalWeight <= 0 ? 0 : completedWeight / totalWeight,
                    remainingMinutes);
        }
    }
}
