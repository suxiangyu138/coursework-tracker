package com.coursework.tracker.service;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.Urgency;
import com.coursework.tracker.util.TimeUtils;
import com.coursework.tracker.util.UrgencyCalculator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地任务分析：不调用 AI 也能给出"现在最该做什么"的判断。
 *
 * <p>主界面 AI 面板顶部用它生成即时洞察，AI 提示词也用它构造任务快照，
 * 保证离线可用，并且模型看到的建议和界面一致。
 */
public final class AdvisorService {

    /** 优先级最高的一项任务。 */
    public record FocusItem(long assignmentId, String courseName, String title,
                            LocalDateTime dueAt, String remaining, double urgency,
                            double weight, int minutes, String reason) {
    }

    /** 当前任务状态快照。 */
    public record TaskBrief(int total, int completed, int active, int overdue, int dueToday,
                            int dueWithin7Days, int remainingMinutes, double weightCompletion,
                            String headline, List<String> insights, List<FocusItem> focus) {

        public TaskBrief {
            insights = List.copyOf(insights);
            focus = List.copyOf(focus);
        }

        public boolean hasData() {
            return total > 0;
        }

        public static TaskBrief empty() {
            return new TaskBrief(0, 0, 0, 0, 0, 0, 0, 0,
                    "还没有作业数据",
                    List.of("先添加课程和作业，我会自动分析优先级、生成今日计划。"),
                    List.of());
        }
    }

    public TaskBrief analyze(List<Course> courses, List<Assignment> assignments,
                             LocalDateTime now, double horizonHours,
                             UrgencyCalculator.Weights weights) {
        if (assignments.isEmpty()) {
            return TaskBrief.empty();
        }

        Map<Long, String> courseNames = new HashMap<>();
        for (Course course : courses) {
            courseNames.put(course.id(), course.name());
        }

        int completed = 0;
        int overdue = 0;
        int dueToday = 0;
        int dueWithin7Days = 0;
        int remainingMinutes = 0;
        double totalWeight = 0;
        double completedWeight = 0;
        List<Assignment> active = new ArrayList<>();
        Map<Long, double[]> remainingByCourse = new HashMap<>();

        for (Assignment assignment : assignments) {
            if (assignment.isCompleted()) {
                completed++;
                if (assignment.weight() > 0) {
                    totalWeight += assignment.weight();
                    completedWeight += assignment.weight();
                }
                continue;
            }
            active.add(assignment);
            if (assignment.weight() > 0) {
                totalWeight += assignment.weight();
            }
            remainingMinutes += assignment.effortMinutesOrDefault(UrgencyCalculator.DEFAULT_EFFORT_MINUTES);
            if (assignment.isOverdue(now)) {
                overdue++;
            }
            if (assignment.dueAt().toLocalDate().equals(now.toLocalDate())) {
                dueToday++;
            }
            if (!assignment.dueAt().isBefore(now) && assignment.dueAt().isBefore(now.plusDays(7))) {
                dueWithin7Days++;
            }
            remainingByCourse.computeIfAbsent(assignment.courseId(), id -> new double[2]);
            double[] courseStats = remainingByCourse.get(assignment.courseId());
            courseStats[0] += Math.max(0, assignment.weight());
            courseStats[1] += 1;
        }

        active.sort(UrgencyCalculator.comparator(now, horizonHours, weights));
        List<FocusItem> focus = new ArrayList<>();
        for (int i = 0; i < Math.min(5, active.size()); i++) {
            Assignment assignment = active.get(i);
            Urgency urgency = UrgencyCalculator.evaluate(assignment, now, horizonHours, weights);
            focus.add(new FocusItem(assignment.id(),
                    courseNames.getOrDefault(assignment.courseId(), "未分类"),
                    assignment.title(), assignment.dueAt(),
                    TimeUtils.remainingText(now, assignment.dueAt()),
                    urgency.score(), assignment.weight(),
                    assignment.effortMinutesOrDefault(UrgencyCalculator.DEFAULT_EFFORT_MINUTES),
                    reasonFor(assignment, now, urgency)));
        }

        double weightCompletion = totalWeight <= 0 ? 0 : completedWeight / totalWeight;
        int total = assignments.size();
        String headline = headline(total, active.size(), overdue, dueToday, dueWithin7Days, focus);
        List<String> insights = new ArrayList<>();
        insights.add("未完成 " + active.size() + " 项：逾期 " + overdue + "、今日截止 " + dueToday
                + "、未来 7 天 " + dueWithin7Days + "。");
        insights.add("预计剩余工作量 " + TimeUtils.humanMinutes(remainingMinutes)
                + "，权重完成度 " + Math.round(weightCompletion * 100) + "%。");
        if (!focus.isEmpty()) {
            FocusItem top = focus.get(0);
            insights.add("最高优先级：" + top.courseName() + " · " + top.title()
                    + "（" + top.reason() + "，权重 " + Math.round(top.weight()) + "%）。");
        }
        String weightPressure = biggestWeightPressure(remainingByCourse, courseNames);
        if (weightPressure != null) {
            insights.add(weightPressure);
        }
        insights.add(suggestion(active, overdue, dueToday, focus));

        return new TaskBrief(total, completed, active.size(), overdue, dueToday,
                dueWithin7Days, remainingMinutes, weightCompletion, headline,
                trim(insights, 5), focus);
    }

    private static String headline(int total, int active, int overdue, int dueToday,
                                   int dueWithin7Days, List<FocusItem> focus) {
        if (total == 0) {
            return "还没有作业数据";
        }
        if (active == 0) {
            return "所有作业都完成了，可以复习或预习";
        }
        String next = focus.isEmpty() ? "" : focus.get(0).title();
        if (overdue > 0) {
            return "有 " + overdue + " 项已逾期，先处理「" + next + "」";
        }
        if (dueToday > 0) {
            return "今天有 " + dueToday + " 项截止，建议先做「" + next + "」";
        }
        if (dueWithin7Days > 0) {
            return "未来 7 天有 " + dueWithin7Days + " 项，最近的是「" + next + "」";
        }
        return "暂时没有临近截止，可推进大作业或复习";
    }

    private static String reasonFor(Assignment assignment, LocalDateTime now, Urgency urgency) {
        List<String> parts = new ArrayList<>();
        if (urgency.overdue()) {
            parts.add(TimeUtils.remainingText(now, assignment.dueAt()));
        } else if (assignment.dueAt().toLocalDate().equals(now.toLocalDate())) {
            parts.add("今天截止");
        } else if (Duration.between(now, assignment.dueAt()).toHours() < 24) {
            parts.add("24 小时内截止");
        } else {
            parts.add(TimeUtils.remainingText(now, assignment.dueAt()));
        }
        if (assignment.weight() >= 30) {
            parts.add("权重 " + Math.round(assignment.weight()) + "%");
        }
        if (assignment.status() == AssignmentStatus.DOING) {
            parts.add("进行中");
        }
        return String.join(" · ", parts);
    }

    private static String biggestWeightPressure(Map<Long, double[]> remainingByCourse,
                                                Map<Long, String> courseNames) {
        Long topCourseId = null;
        double topWeight = 0;
        double topCount = 0;
        for (Map.Entry<Long, double[]> entry : remainingByCourse.entrySet()) {
            if (entry.getValue()[0] > topWeight) {
                topWeight = entry.getValue()[0];
                topCount = entry.getValue()[1];
                topCourseId = entry.getKey();
            }
        }
        if (topCourseId == null || topWeight <= 0) {
            return null;
        }
        return "权重压力最大：" + courseNames.getOrDefault(topCourseId, "未分类")
                + "（未完成 " + (int) topCount + " 项，共 " + Math.round(topWeight) + "% 权重）。";
    }

    private static String suggestion(List<Assignment> active, int overdue, int dueToday,
                                     List<FocusItem> focus) {
        if (active.isEmpty()) {
            return "目前没有待完成作业，可以复习错题、整理笔记或提前准备下一门课。";
        }
        FocusItem top = focus.isEmpty() ? null : focus.get(0);
        String target = top == null ? "最紧急的一项" : "「" + top.title() + "」";
        if (overdue > 0) {
            return "建议先清理逾期项" + target + "，再按紧急度推进；逾期越久对总评影响越大。";
        }
        if (dueToday > 0) {
            return "今天优先完成" + target + "，剩下的时间至少给每项任务推进 30 分钟。";
        }
        return "建议今晚先做" + target + "，完成后再处理权重较低的任务。";
    }

    private static List<String> trim(List<String> values, int limit) {
        return values.size() <= limit ? values : new ArrayList<>(values.subList(0, limit));
    }

    /** 构造给 AI 的任务快照文本（也用于调试与测试）。 */
    public String describe(TaskBrief brief) {
        StringBuilder builder = new StringBuilder(brief.headline()).append('\n');
        for (String insight : brief.insights()) {
            builder.append("- ").append(insight).append('\n');
        }
        if (!brief.focus().isEmpty()) {
            builder.append("优先任务：\n");
            for (FocusItem item : brief.focus()) {
                builder.append("  * ").append(item.courseName()).append(" · ").append(item.title())
                        .append(" | 截止 ").append(item.dueAt())
                        .append(" | ").append(item.remaining())
                        .append(" | 权重 ").append(Math.round(item.weight())).append("%")
                        .append(" | 预计 ").append(item.minutes()).append(" 分钟\n");
            }
        }
        return builder.toString().trim();
    }
}
