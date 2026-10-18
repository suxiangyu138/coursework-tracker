package com.coursework.tracker.ai;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.CompletionStats;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseGrade;
import com.coursework.tracker.model.CourseProgress;
import com.coursework.tracker.model.Semester;
import com.coursework.tracker.model.Urgency;
import com.coursework.tracker.service.AdvisorService;
import com.coursework.tracker.util.Formatters;
import com.coursework.tracker.util.TimeUtils;
import com.coursework.tracker.util.UrgencyCalculator;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一的 AI 系统提示词：当前时间、学期、任务快照、课程进度与行为规则。
 *
 * <p>主界面 AI 面板与完整对话窗口共用，保证两边看到的上下文和智能程度一致。
 * 任务快照由本地 {@link AdvisorService} 生成，AI 不在线时界面也能给建议。
 */
public final class AiPromptBuilder {

    private AiPromptBuilder() {
    }

    public static String build(AppContext context) {
        LocalDateTime now = ClockProvider.now();
        List<Course> courses = context.courseService().list();
        List<Assignment> assignments = context.assignmentService().listAll();
        CompletionStats stats = context.statisticsService().compute(courses, assignments);
        AdvisorService advisor = new AdvisorService();
        AdvisorService.TaskBrief brief = advisor.analyze(courses, assignments, now,
                context.settings().horizonHours(), context.settings().weights());
        List<CourseGrade> grades = context.gradeService().compute(courses, assignments);
        Map<Long, CourseGrade> gradeByCourse = new HashMap<>();
        for (CourseGrade grade : grades) {
            gradeByCourse.put(grade.courseId(), grade);
        }
        Semester currentSemester = Semester.current(now);

        StringBuilder prompt = new StringBuilder();
        prompt.append("你既是「作业哨兵」的作业管理助手，也是一个通用的中文写作与理解助手。\n");
        prompt.append("当前时间：").append(Formatters.display(now)).append(' ')
                .append(TimeUtils.weekday(now)).append('\n');
        prompt.append("当前学期：").append(currentSemester.label())
                .append("（编码 ").append(currentSemester.code()).append("）\n");
        prompt.append("数据概况：").append(courses.size()).append(" 门课程、")
                .append(assignments.size()).append(" 条作业；已完成 ")
                .append(stats.completed()).append(" 条，完成率 ")
                .append(stats.countRateText()).append("，权重完成度 ")
                .append(stats.weightRateText()).append("，预计 GPA ")
                .append(String.format("%.2f", context.gradeService().gpa(grades))).append("。\n\n");

        prompt.append("【任务状态快照】\n");
        prompt.append(brief.headline()).append('\n');
        for (String insight : brief.insights()) {
            prompt.append("- ").append(insight).append('\n');
        }
        if (!brief.focus().isEmpty()) {
            prompt.append("优先任务（按紧急度排序）：\n");
            for (int i = 0; i < brief.focus().size(); i++) {
                AdvisorService.FocusItem item = brief.focus().get(i);
                prompt.append(i + 1).append(". ").append(item.courseName())
                        .append(" · ").append(item.title())
                        .append(" | 截止 ").append(Formatters.display(item.dueAt()))
                        .append(" | ").append(item.remaining())
                        .append(" | 权重 ").append(Math.round(item.weight())).append("%")
                        .append(" | 预计 ").append(item.minutes()).append(" 分钟")
                        .append(" | 紧急度 ").append(String.format("%.2f", item.urgency()))
                        .append(" | ").append(item.reason()).append('\n');
            }
        }

        appendActiveAssignments(prompt, assignments, courses, now, context);
        appendCourses(prompt, courses, stats, gradeByCourse);

        prompt.append("\n【行为规则】\n");
        prompt.append("""
                1. 用简体中文回答，简洁直接；输出纯文本，不要使用 Markdown（不要用 **、#、反引号、代码块、表格），
                   需要分点时用“1.”“·”和换行。
                2. 用户让你改写、润色、总结、解释、翻译、列提纲、分析长文时，直接完成，
                   不要调用工具，也不要强行关联课程作业。
                3. 面对模糊或复杂的请求：先用一句话复述你的理解，指出缺失的关键信息
                   （哪门课、哪条作业、什么时间、权重多少），向用户提问；信息不足时不要猜测执行。
                4. 信息足够时，先简述执行计划，然后直接调用工具；执行计划会由界面弹窗让用户确认，
                   不要在文字里停下来等待确认。
                5. 涉及批量修改（推迟、统一截止、统一状态）时，必须给出筛选条件和唯一改动，
                   先用 list_assignments 确认范围，再调用 batch_update_assignments。
                6. 只有明确涉及课程/作业数据的增删改查时，才调用工具，不要只在文字里描述。
                   纯建议、规划、分析类请求直接基于上面的任务快照回答，不要为了回答而修改数据。
                7. 日期时间格式统一为 yyyy-MM-dd HH:mm。
                8. 课程相关操作前，先调用 list_courses 查看已有课程（会返回课程 ID、学期、作业数）。
                   课程名可能因为空格、全半角括号、大小写或一两个字的差别而看起来不同，
                   这类情况一律视为同一门课，直接复用已有课程，不要新建。
                9. 只有工具返回「未创建：检测到相似课程」且用户明确确认这是另一门课时，
                   才用 forceNew=true 重新创建；不要连续重试创建。
                10. 同一门课不要为了换学期重复建课，用 update_course 修改学期。
                    用户说「下学期的某课」时，传学期编码，例如 2027-2028-1（2027–2028 学年第一学期/秋季）。
                11. 用户要删除课程时用 delete_course；它会级联删除该课程下的全部作业，
                    确认框会显示作业数量。只删除某条作业时用 delete_assignment，不要混用。
                12. 不确定课程或作业是否存在时，先查询（list_courses / list_assignments）；
                    课程同名或相似时优先使用课程 ID 精确定位。不要编造不存在的课程或作业。
                13. 工具执行失败时如实说明原因；用户拒绝执行后不要再次尝试同样的修改。
                14. 用户问「今天先做什么 / 怎么安排 / 帮我计划」时，必须给出可以直接执行的时间表：
                    - 先说明假设的可用时间；用户没说时按 19:00–23:00 规划，并注明这是默认假设。
                    - 按紧急度、截止时间、权重排序，不要平均分配时间。
                    - 每个时间段写清：时间、课程 · 作业、预计时长、完成标准/产出。
                    - 如果预计总耗时超过可用时间，明确指出必须延后或放弃哪一项。
                    - 优先安排已逾期和 24 小时内截止的任务，并留出 10 分钟缓冲。
                    - 最后给 3 条执行建议（先做哪项、如何避免分心、什么时候检查进度）。
                15. 用户问「这周有什么风险 / 会不会逾期 / 怎么提高」时，先给风险等级（高/中/低），
                    再给原因和下一步行动；必须引用具体课程名、作业名、截止时间、权重或完成度，
                    不要给放之四海而皆准的空话。
                16. 规划要现实：单个专注时段建议 25–50 分钟，连续不超过 2 小时；不要排满每一分钟。
                17. 如果任务快照为空或数据不足，直接说明缺什么，不要编造课程或作业。
                """);
        return prompt.toString();
    }

    private static void appendActiveAssignments(StringBuilder prompt, List<Assignment> assignments,
                                                List<Course> courses, LocalDateTime now,
                                                AppContext context) {
        java.util.Map<Long, String> courseNames = new java.util.HashMap<>();
        for (Course course : courses) {
            courseNames.put(course.id(), course.name());
        }
        List<Assignment> active = assignments.stream()
                .filter(assignment -> !assignment.isCompleted())
                .sorted(UrgencyCalculator.comparator(now, context.settings().horizonHours(),
                        context.settings().weights()))
                .limit(12)
                .toList();
        if (active.isEmpty()) {
            prompt.append("\n【未完成作业】\n暂无未完成作业。\n");
            return;
        }
        prompt.append("\n【未完成作业（最多 12 条）】\n");
        for (Assignment assignment : active) {
            Urgency urgency = UrgencyCalculator.evaluate(assignment, now,
                    context.settings().horizonHours(), context.settings().weights());
            prompt.append("- ").append(courseNames.getOrDefault(assignment.courseId(), "未分类"))
                    .append(" · ").append(assignment.title())
                    .append(" | 截止 ").append(Formatters.display(assignment.dueAt()))
                    .append(" | ").append(TimeUtils.remainingText(now, assignment.dueAt()))
                    .append(" | 权重 ").append(Math.round(assignment.weight())).append("%")
                    .append(" | 状态 ").append(assignment.status().label())
                    .append(" | 预计 ").append(assignment.effortMinutesOrDefault(
                            UrgencyCalculator.DEFAULT_EFFORT_MINUTES)).append(" 分钟")
                    .append(" | 紧急度 ").append(urgency.displayScore()).append('\n');
        }
    }

    private static void appendCourses(StringBuilder prompt, List<Course> courses,
                                      CompletionStats stats,
                                      Map<Long, CourseGrade> gradeByCourse) {
        if (courses.isEmpty()) {
            prompt.append("\n【课程】\n暂无课程。\n");
            return;
        }
        prompt.append("\n【课程与进度】\n");
        List<Course> sorted = courses.stream()
                .sorted(Comparator.comparing(Course::name))
                .limit(20)
                .toList();
        for (Course course : sorted) {
            CourseProgress progress = stats.byCourse().get(course.id());
            prompt.append("- ").append(course.name())
                    .append("（ID ").append(course.id()).append("）")
                    .append(" | ").append(Semester.displayShort(course.semester()))
                    .append(" | ").append(course.credit()).append(" 学分");
            if (course.teacher() != null) {
                prompt.append(" | 教师 ").append(course.teacher());
            }
            if (course.targetScore() != null) {
                prompt.append(" | 目标分 ").append(course.targetScore());
            }
            if (progress != null) {
                prompt.append(" | 作业 ").append(progress.completed()).append('/')
                        .append(progress.total()).append(" 完成");
                if (progress.total() > 0) {
                    prompt.append(" | 权重完成度 ")
                            .append(Math.round(progress.weightRate() * 100)).append('%');
                }
            }
            CourseGrade grade = gradeByCourse.get(course.id());
            if (grade != null && course.targetScore() != null) {
                prompt.append(" | 预计总评 ").append(grade.projectedText())
                        .append(" / 目标 ").append(Math.round(course.targetScore()))
                        .append(" | 剩余需 ").append(grade.neededText());
            }
            prompt.append('\n');
        }
        if (courses.size() > sorted.size()) {
            prompt.append("… 其余 ").append(courses.size() - sorted.size()).append(" 门课程未列出\n");
        }
    }
}
