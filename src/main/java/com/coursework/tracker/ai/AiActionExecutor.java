package com.coursework.tracker.ai;

import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.Semester;
import com.coursework.tracker.service.AssignmentService;
import com.coursework.tracker.service.BackupService;
import com.coursework.tracker.service.BusinessException;
import com.coursework.tracker.service.CourseService;
import com.coursework.tracker.util.CourseNameMatcher;
import com.coursework.tracker.util.Formatters;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 执行 AI 工具调用。所有写操作由调用方先做用户确认；这里只负责解析参数、调用 Service、返回可读结果。
 */
public final class AiActionExecutor {

    private static final Set<String> READ_ONLY_TOOLS =
            Set.of(AiTools.LIST_ASSIGNMENTS, AiTools.LIST_COURSES);
    private static final double SIMILAR_THRESHOLD = 0.55;

    private final CourseService courseService;
    private final AssignmentService assignmentService;
    private final BackupService backupService;

    public AiActionExecutor(CourseService courseService, AssignmentService assignmentService,
                            BackupService backupService) {
        this.courseService = courseService;
        this.assignmentService = assignmentService;
        this.backupService = backupService;
    }

    public boolean isMutating(String toolName) {
        return !READ_ONLY_TOOLS.contains(toolName);
    }

    /** 给确认对话框展示的人类可读描述。 */
    public String preview(DeepSeekClient.ToolCall call) {
        try {
            JsonObject args = args(call);
            return switch (call.name()) {
                case AiTools.CREATE_COURSE -> createCoursePreview(args);
                case AiTools.UPDATE_COURSE -> updateCoursePreview(args);
                case AiTools.DELETE_COURSE -> deleteCoursePreview(args);
                case AiTools.LIST_COURSES -> "查询课程：" + courseFilterText(args);
                case AiTools.CREATE_ASSIGNMENT -> "新建作业：" + text(args, "course") + " · "
                        + text(args, "title") + "，截止 " + formatDueText(text(args, "dueAt"))
                        + "，权重 " + number(args, "weight", 0) + "%";
                case AiTools.UPDATE_ASSIGNMENT -> "修改作业：" + label(args) + " → " + updateDetails(args);
                case AiTools.DELETE_ASSIGNMENT -> "删除作业：" + label(args);
                case AiTools.BATCH_UPDATE_ASSIGNMENTS -> batchPreview(args);
                case AiTools.LIST_ASSIGNMENTS -> "查询作业：" + filterText(args);
                default -> call.name();
            };
        } catch (AiException | RuntimeException e) {
            return call.name();
        }
    }

    public String execute(DeepSeekClient.ToolCall call) throws AiException {
        try {
            JsonObject args = args(call);
            return switch (call.name()) {
                case AiTools.CREATE_COURSE -> createCourse(args);
                case AiTools.UPDATE_COURSE -> updateCourse(args);
                case AiTools.DELETE_COURSE -> deleteCourse(args);
                case AiTools.LIST_COURSES -> listCourses(args);
                case AiTools.CREATE_ASSIGNMENT -> createAssignment(args);
                case AiTools.UPDATE_ASSIGNMENT -> updateAssignment(args);
                case AiTools.DELETE_ASSIGNMENT -> deleteAssignment(args);
                case AiTools.BATCH_UPDATE_ASSIGNMENTS -> batchUpdate(args);
                case AiTools.LIST_ASSIGNMENTS -> listAssignments(args);
                default -> throw new AiException("不支持的工具：" + call.name());
            };
        } catch (BusinessException e) {
            throw new AiException(e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new AiException(e.getMessage(), e);
        }
    }

    private String createCourse(JsonObject args) throws AiException {
        String name = required(args, "name");
        boolean forceNew = bool(args, "forceNew");

        Course exact = findExactCourse(name);
        if (exact != null) {
            if (forceNew) {
                throw new AiException("课程「" + exact.name() + "」已存在（" + exact.semesterLabel()
                        + "，ID=" + exact.id() + "）。课程名需全局唯一，请直接复用已有课程，"
                        + "或换一个更具体的课程名。");
            }
            return "课程「" + exact.name() + "」已存在（" + exact.semesterLabel()
                    + "，ID=" + exact.id() + "），已直接复用，没有重复创建。";
        }
        if (!forceNew) {
            List<Course> similar = similarCourses(name, 3);
            if (!similar.isEmpty()) {
                return "未创建课程「" + name + "」：检测到可能重复的已有课程 "
                        + courseListText(similar)
                        + "。请先确认是否复用已有课程；如果确实是另一门课，"
                        + "请让用户补充更具体的名称，或确认后再用 forceNew=true 创建。";
            }
        }

        double credit = number(args, "credit", 3);
        String teacher = optionalText(args, "teacher");
        Double target = args.has("targetScore") && !args.get("targetScore").isJsonNull()
                ? args.get("targetScore").getAsDouble() : null;
        String semester = resolveSemester(args);
        Course course = courseService.create(name, null, credit, teacher, semester, target);
        return "已新建课程：" + course.name() + "（" + course.credit() + " 学分 · "
                + course.semesterLabel() + "，ID=" + course.id() + "）";
    }

    private String createCoursePreview(JsonObject args) {
        String name = text(args, "name");
        Course exact = findExactCourse(name);
        if (exact != null) {
            return "新建课程：" + name + " → 已存在（" + exact.semesterLabel() + "，ID="
                    + exact.id() + "），将复用，不会重复创建";
        }
        List<Course> similar = similarCourses(name, 3);
        if (!similar.isEmpty() && !bool(args, "forceNew")) {
            return "新建课程：" + name + " → 检测到相似课程 " + courseListText(similar)
                    + "，将暂不创建并请你确认";
        }
        return "新建课程：" + name + "（" + number(args, "credit", 3) + " 学分，"
                + semesterLabel(args) + "）";
    }

    private String updateCourse(JsonObject args) throws AiException {
        Course existing = findCourse(required(args, "course"));
        String name = existing.name();
        if (args.has("newName")) {
            String newName = optionalText(args, "newName");
            if (newName == null) {
                throw new AiException("新课程名不能为空");
            }
            name = newName;
        }
        double credit = existing.credit();
        if (args.has("credit") && !args.get("credit").isJsonNull()) {
            credit = args.get("credit").getAsDouble();
        }
        String teacher = existing.teacher();
        if (args.has("teacher")) {
            teacher = optionalText(args, "teacher");
        }
        String semester = existing.semester();
        if (args.has("semester")) {
            semester = parseSemesterValue(args.get("semester"));
        }
        Double target = existing.targetScore();
        if (args.has("targetScore")) {
            JsonElement element = args.get("targetScore");
            if (element == null || element.isJsonNull()) {
                target = null;
            } else {
                double value = element.getAsDouble();
                target = value <= 0 ? null : value;
            }
        }

        Course updated = new Course(existing.id(), name, existing.code(), credit, teacher,
                semester, target, existing.archived(), existing.createdAt());
        if (existing.equals(updated)) {
            throw new AiException("没有提供要修改的字段，课程未发生变化");
        }
        courseService.update(updated);
        return "已更新课程：" + updated.name() + "（" + updated.semesterLabel()
                + "，ID=" + updated.id() + "）";
    }

    private String updateCoursePreview(JsonObject args) {
        try {
            Course course = findCourse(required(args, "course"));
            List<String> changes = new ArrayList<>();
            if (args.has("newName") && optionalText(args, "newName") != null) {
                changes.add("课程名改为「" + optionalText(args, "newName") + "」");
            }
            if (args.has("semester")) {
                changes.add("学期改为 " + Semester.display(parseSemesterValue(args.get("semester"))));
            }
            if (args.has("credit") && !args.get("credit").isJsonNull()) {
                changes.add("学分改为 " + formatNumber(args.get("credit")));
            }
            if (args.has("teacher")) {
                String teacher = optionalText(args, "teacher");
                changes.add(teacher == null ? "清空教师" : "教师改为 " + teacher);
            }
            if (args.has("targetScore")) {
                JsonElement element = args.get("targetScore");
                if (element == null || element.isJsonNull() || element.getAsDouble() <= 0) {
                    changes.add("清空目标分");
                } else {
                    changes.add("目标分改为 " + formatNumber(element));
                }
            }
            return "修改课程：" + course.name() + " → "
                    + (changes.isEmpty() ? "未指定字段" : String.join("，", changes));
        } catch (AiException | RuntimeException e) {
            return "修改课程：" + text(args, "course");
        }
    }

    private String deleteCourse(JsonObject args) throws AiException {
        Course course = findCourse(required(args, "course"));
        long count = courseService.assignmentCount(course.id());
        try {
            backupService.backupNow();
            courseService.delete(course.id());
        } catch (RuntimeException e) {
            throw new AiException("删除课程失败（删除前已尝试备份）：" + e.getMessage(), e);
        }
        return "已删除课程：" + course.name() + "（同时删除 " + count
                + " 条作业及其提醒记录，删除前已备份数据库）";
    }

    private String deleteCoursePreview(JsonObject args) {
        try {
            Course course = findCourse(required(args, "course"));
            long count = courseService.assignmentCount(course.id());
            return "删除课程：" + course.name() + "（" + course.semesterLabel() + "）"
                    + "，会同时删除 " + count + " 条作业及其提醒记录；删除前自动备份数据库，不可撤销";
        } catch (AiException | RuntimeException e) {
            return "删除课程：" + text(args, "course") + "（会级联删除该课程下的全部作业）";
        }
    }

    private String listCourses(JsonObject args) throws AiException {
        List<Course> courses = new ArrayList<>(courseService.list());
        String semesterFilter = optionalText(args, "semester");
        if (semesterFilter != null) {
            if (isUnassignedSemester(semesterFilter)) {
                courses.removeIf(course -> Semester.normalize(course.semester()) != null);
            } else {
                String code = Semester.parse(semesterFilter).map(Semester::code)
                        .orElse(semesterFilter.trim());
                courses.removeIf(course -> !code.equals(Semester.normalize(course.semester())));
            }
        }
        String keyword = optionalText(args, "keyword");
        if (keyword != null) {
            String normalized = CourseNameMatcher.normalize(keyword);
            courses.removeIf(course -> !CourseNameMatcher.normalize(course.name()).contains(normalized)
                    && (course.teacher() == null
                    || !CourseNameMatcher.normalize(course.teacher()).contains(normalized)));
        }
        if (courses.isEmpty()) {
            return "没有符合条件的课程。";
        }

        Map<Long, Integer> assignmentCounts = new HashMap<>();
        for (Assignment assignment : assignmentService.listAll()) {
            assignmentCounts.merge(assignment.courseId(), 1, Integer::sum);
        }

        Map<String, List<Course>> groups = new LinkedHashMap<>();
        for (Course course : courses) {
            String key = Semester.normalize(course.semester()) == null
                    ? "" : Semester.normalize(course.semester());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(course);
        }
        List<String> semesters = new ArrayList<>(groups.keySet());
        semesters.sort(AiActionExecutor::compareSemesterKeys);

        StringBuilder builder = new StringBuilder("共 ").append(courses.size()).append(" 门课程：\n");
        for (String semester : semesters) {
            builder.append("【").append(Semester.display(semester)).append("】\n");
            List<Course> group = groups.get(semester);
            group.sort(Comparator.comparing(Course::name));
            for (Course course : group) {
                builder.append("- ").append(course.name())
                        .append("（ID ").append(course.id()).append("）")
                        .append(" | ").append(course.credit()).append(" 学分")
                        .append(" | 教师 ").append(course.teacher() == null ? "—" : course.teacher())
                        .append(" | ").append(assignmentCounts.getOrDefault(course.id(), 0))
                        .append(" 项作业\n");
            }
        }
        return builder.toString().trim();
    }

    private static String courseFilterText(JsonObject args) {
        List<String> parts = new ArrayList<>();
        String semester = optionalText(args, "semester");
        if (semester != null) {
            parts.add("学期=" + Semester.display(semester));
        }
        String keyword = optionalText(args, "keyword");
        if (keyword != null) {
            parts.add("关键词=" + keyword);
        }
        return parts.isEmpty() ? "全部课程" : String.join("，", parts);
    }

    /** 学期参数：合法编码就用编码；省略/无法识别时使用当前学期。 */
    private static String resolveSemester(JsonObject args) {
        String value = optionalText(args, "semester");
        if (value == null) {
            return Semester.current(ClockProvider.now()).code();
        }
        return parseSemesterValue(value);
    }

    private static String parseSemesterValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return parseSemesterValue(element.getAsString());
    }

    private static String parseSemesterValue(String value) {
        String text = Semester.normalize(value);
        if (text == null || isUnassignedSemester(text)) {
            return null;
        }
        return Semester.parse(text).map(Semester::code).orElse(text);
    }

    private static boolean isUnassignedSemester(String value) {
        if (value == null) {
            return true;
        }
        String text = value.trim();
        return text.isEmpty() || "未分学期".equals(text) || "未分类".equals(text)
                || "none".equalsIgnoreCase(text);
    }

    private static String semesterLabel(JsonObject args) {
        String value = optionalText(args, "semester");
        if (value == null) {
            return Semester.current(ClockProvider.now()).label();
        }
        return Semester.display(value);
    }

    private Course findExactCourse(String name) {
        String normalized = CourseNameMatcher.normalize(name);
        if (normalized.isEmpty()) {
            return null;
        }
        for (Course course : courseService.list()) {
            if (CourseNameMatcher.normalize(course.name()).equals(normalized)) {
                return course;
            }
        }
        return null;
    }

    private List<Course> similarCourses(String name, int limit) {
        String normalized = CourseNameMatcher.normalize(name);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<Course> candidates = new ArrayList<>();
        for (Course course : courseService.list()) {
            if (CourseNameMatcher.similarity(name, course.name()) >= SIMILAR_THRESHOLD) {
                candidates.add(course);
            }
        }
        candidates.sort(Comparator.comparingDouble(
                (Course course) -> CourseNameMatcher.similarity(name, course.name())).reversed());
        return candidates.size() <= limit ? candidates : new ArrayList<>(candidates.subList(0, limit));
    }

    private static String courseListText(List<Course> courses) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            if (i > 0) {
                builder.append("、");
            }
            builder.append("「").append(course.name()).append("」（")
                    .append(course.semesterLabel()).append("，ID=").append(course.id()).append("）");
        }
        return builder.toString();
    }

    private static int compareSemesterKeys(String left, String right) {
        java.util.Optional<Semester> leftSemester = Semester.parse(left);
        java.util.Optional<Semester> rightSemester = Semester.parse(right);
        if (leftSemester.isPresent() && rightSemester.isPresent()) {
            return rightSemester.get().compareTo(leftSemester.get());
        }
        if (leftSemester.isPresent()) {
            return -1;
        }
        if (rightSemester.isPresent()) {
            return 1;
        }
        boolean leftBlank = left == null || left.isBlank();
        boolean rightBlank = right == null || right.isBlank();
        if (leftBlank && rightBlank) {
            return 0;
        }
        if (leftBlank) {
            return 1;
        }
        if (rightBlank) {
            return -1;
        }
        return left.compareTo(right);
    }

    private String createAssignment(JsonObject args) throws AiException {
        String courseName = required(args, "course");
        Course course = findCourse(courseName);
        String title = required(args, "title");
        LocalDateTime dueAt = parseDue(required(args, "dueAt"));
        double weight = number(args, "weight", 0);
        Integer estimated = args.has("estimatedMinutes") && !args.get("estimatedMinutes").isJsonNull()
                ? args.get("estimatedMinutes").getAsInt() : null;
        String notes = optionalText(args, "notes");

        Assignment draft = Assignment.draft(course.id(), title, dueAt, weight, estimated, notes,
                ClockProvider.now());
        Assignment created = assignmentService.create(draft);
        return "已创建作业：" + course.name() + " · " + created.title()
                + "，截止 " + Formatters.display(created.dueAt())
                + "，权重 " + Formatters.weight(created.weight());
    }

    private String updateAssignment(JsonObject args) throws AiException {
        Assignment existing = findAssignment(required(args, "title"), optionalText(args, "course"));
        Assignment updated = existing;
        boolean changed = false;

        if (args.has("newTitle") && !args.get("newTitle").isJsonNull()) {
            updated = updated.withTitle(args.get("newTitle").getAsString());
            changed = true;
        }
        if (args.has("dueAt") && !args.get("dueAt").isJsonNull()) {
            updated = updated.withDueAt(parseDue(args.get("dueAt").getAsString()));
            changed = true;
        }
        if (args.has("weight") && !args.get("weight").isJsonNull()) {
            updated = updated.withWeight(args.get("weight").getAsDouble());
            changed = true;
        }
        if (args.has("estimatedMinutes") && !args.get("estimatedMinutes").isJsonNull()) {
            updated = updated.withEstimatedMinutes(args.get("estimatedMinutes").getAsInt());
            changed = true;
        }
        if (args.has("score") && !args.get("score").isJsonNull()) {
            updated = updated.withScore(args.get("score").getAsDouble());
            changed = true;
        }
        if (args.has("notes") && !args.get("notes").isJsonNull()) {
            updated = updated.withNotes(args.get("notes").getAsString());
            changed = true;
        }
        if (args.has("status") && !args.get("status").isJsonNull()) {
            AssignmentStatus status = parseStatus(args.get("status").getAsString());
            updated = updated.withStatus(status)
                    .withSubmittedAt(status.isCompleted() ? ClockProvider.now() : null);
            changed = true;
        }
        if (!changed) {
            throw new AiException("没有提供要修改的字段");
        }

        Assignment saved = assignmentService.update(updated.withUpdatedAt(ClockProvider.now()));
        return "已更新作业：" + saved.title();
    }

    private String deleteAssignment(JsonObject args) throws AiException {
        Assignment assignment = findAssignment(required(args, "title"), optionalText(args, "course"));
        assignmentService.delete(assignment.id());
        return "已删除作业：" + assignment.title();
    }

    private String batchPreview(JsonObject args) {
        try {
            List<Assignment> matches = selectForBatch(args);
            return "批量修改作业：" + filterText(args) + " → " + changeText(args)
                    + "（命中 " + matches.size() + " 条"
                    + (matches.isEmpty() ? "" : "：" + sampleTitles(matches, 3)) + "）";
        } catch (AiException | RuntimeException e) {
            return "批量修改作业（筛选条件或改动不完整）";
        }
    }

    private String batchUpdate(JsonObject args) throws AiException {
        List<Assignment> matches = selectForBatch(args);
        if (matches.isEmpty()) {
            throw new AiException("没有符合条件的作业，未做修改");
        }
        LocalDateTime now = ClockProvider.now();
        Integer postponeDays = args.has("postponeDays") && !args.get("postponeDays").isJsonNull()
                ? args.get("postponeDays").getAsInt() : null;
        LocalDateTime setDueAt = optionalDate(args, "setDueAt");
        AssignmentStatus setStatus = args.has("setStatus") && !args.get("setStatus").isJsonNull()
                ? parseStatus(args.get("setStatus").getAsString()) : null;

        for (Assignment assignment : matches) {
            Assignment updated = assignment;
            if (postponeDays != null) {
                updated = updated.withDueAt(assignment.dueAt().plusDays(postponeDays));
            } else if (setDueAt != null) {
                updated = updated.withDueAt(setDueAt);
            } else if (setStatus != null) {
                updated = updated.withStatus(setStatus)
                        .withSubmittedAt(setStatus.isCompleted() ? now : null);
            }
            assignmentService.update(updated.withUpdatedAt(now));
        }
        return "已批量更新 " + matches.size() + " 条：" + sampleTitles(matches, 5)
                + (matches.size() > 5 ? " 等" : "");
    }

    /**
     * 批量筛选：至少一个条件，避免模型误改全部作业。
     */
    private List<Assignment> selectForBatch(JsonObject args) throws AiException {
        String courseText = optionalText(args, "course");
        String statusFilter = optionalText(args, "status");
        boolean unfinished = bool(args, "unfinished");
        LocalDateTime from = optionalDate(args, "from");
        LocalDateTime to = optionalDate(args, "to");

        if (courseText == null && statusFilter == null && !unfinished && from == null && to == null) {
            throw new AiException("批量操作至少要有一个筛选条件（course / status / unfinished / from / to）");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new AiException("from 不能晚于 to");
        }

        int changes = 0;
        if (args.has("postponeDays") && !args.get("postponeDays").isJsonNull()) {
            changes++;
            if (args.get("postponeDays").getAsInt() == 0) {
                throw new AiException("postponeDays 不能为 0");
            }
        }
        if (args.has("setDueAt") && !args.get("setDueAt").isJsonNull()) {
            changes++;
        }
        if (args.has("setStatus") && !args.get("setStatus").isJsonNull()) {
            changes++;
        }
        if (changes != 1) {
            throw new AiException("批量修改需要且只能提供一个改动（postponeDays / setDueAt / setStatus）");
        }
        Course courseFilter = courseText == null ? null : findCourse(courseText);

        AssignmentStatus status = statusFilter == null ? null : parseStatus(statusFilter);
        List<Assignment> matches = new ArrayList<>();
        for (Assignment assignment : assignmentService.listAll()) {
            if (courseFilter != null && assignment.courseId() != courseFilter.id()) {
                continue;
            }
            if (status != null && assignment.status() != status) {
                continue;
            }
            if (unfinished && assignment.status().isCompleted()) {
                continue;
            }
            if (from != null && assignment.dueAt().isBefore(from)) {
                continue;
            }
            if (to != null && assignment.dueAt().isAfter(to)) {
                continue;
            }
            matches.add(assignment);
        }
        return matches;
    }

    private String filterText(JsonObject args) {
        List<String> parts = new ArrayList<>();
        String course = optionalText(args, "course");
        if (course != null) {
            parts.add("课程=" + course);
        }
        String status = optionalText(args, "status");
        if (status != null) {
            parts.add("状态=" + statusLabel(status));
        }
        if (bool(args, "unfinished")) {
            parts.add("未完成");
        }
        String from = optionalText(args, "from");
        if (from != null) {
            parts.add("截止 ≥ " + formatDueText(from));
        }
        String to = optionalText(args, "to");
        if (to != null) {
            parts.add("截止 ≤ " + formatDueText(to));
        }
        return parts.isEmpty() ? "全部作业" : String.join("，", parts);
    }

    private String changeText(JsonObject args) {
        Integer postponeDays = args.has("postponeDays") && !args.get("postponeDays").isJsonNull()
                ? args.get("postponeDays").getAsInt() : null;
        if (postponeDays != null) {
            return postponeDays > 0 ? "截止推迟 " + postponeDays + " 天"
                    : "截止提前 " + (-postponeDays) + " 天";
        }
        String setDueAt = optionalText(args, "setDueAt");
        if (setDueAt != null) {
            return "截止改为 " + formatDueText(setDueAt);
        }
        String setStatus = optionalText(args, "setStatus");
        if (setStatus != null) {
            return "状态改为 " + statusLabel(setStatus);
        }
        return "无改动";
    }

    private static String sampleTitles(List<Assignment> assignments, int limit) {
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, assignments.size()); i++) {
            titles.add(assignments.get(i).title());
        }
        return String.join("、", titles);
    }

    private static LocalDateTime optionalDate(JsonObject args, String key) throws AiException {
        String value = optionalText(args, key);
        return value == null || value.isBlank() ? null : parseDue(value);
    }

    private static boolean bool(JsonObject args, String key) {
        JsonElement element = args.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private String listAssignments(JsonObject args) throws AiException {
        String courseText = optionalText(args, "course");
        String statusFilter = optionalText(args, "status");
        Integer withinDays = args.has("withinDays") && !args.get("withinDays").isJsonNull()
                ? args.get("withinDays").getAsInt() : null;

        Course courseFilter = courseText == null ? null : findCourse(courseText);
        Map<Long, String> courseNames = courseNames();
        AssignmentStatus status = statusFilter == null ? null : parseStatus(statusFilter);
        LocalDateTime now = ClockProvider.now();
        LocalDateTime limit = withinDays == null ? null : now.plusDays(withinDays);

        List<Assignment> matches = new ArrayList<>();
        for (Assignment assignment : assignmentService.listAll()) {
            if (courseFilter != null && assignment.courseId() != courseFilter.id()) {
                continue;
            }
            if (status != null && assignment.status() != status) {
                continue;
            }
            if (limit != null && assignment.dueAt().isAfter(limit)) {
                continue;
            }
            matches.add(assignment);
        }
        matches.sort(Comparator.comparing(Assignment::dueAt));

        if (matches.isEmpty()) {
            return "没有符合条件的作业。";
        }
        StringBuilder builder = new StringBuilder("共 ").append(matches.size()).append(" 条：\n");
        int limitCount = Math.min(40, matches.size());
        for (int i = 0; i < limitCount; i++) {
            Assignment assignment = matches.get(i);
            builder.append("- ")
                    .append(courseNames.getOrDefault(assignment.courseId(), "未分类"))
                    .append(" · ").append(assignment.title())
                    .append(" | 截止 ").append(Formatters.display(assignment.dueAt()))
                    .append(" | ").append(assignment.status().label())
                    .append(" | 权重 ").append(Formatters.weight(assignment.weight()))
                    .append('\n');
        }
        if (matches.size() > limitCount) {
            builder.append("… 其余 ").append(matches.size() - limitCount).append(" 条未列出");
        }
        return builder.toString().trim();
    }

    // ------------------------------------------------------------------
    // 参数与查找
    // ------------------------------------------------------------------

    private JsonObject args(DeepSeekClient.ToolCall call) throws AiException {
        String raw = call.arguments();
        if (raw == null || raw.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new AiException("工具参数不是合法 JSON：" + raw);
        }
    }

    /**
     * 课程定位：支持课程 ID、精确名称、归一化名称（空格/全半角/大小写不敏感）。
     * 名称只是相似时不会猜测，而是把候选课程列出来让模型向用户确认。
     */
    private Course findCourse(String reference) throws AiException {
        List<Course> courses = courseService.list();
        Long id = parseId(reference);
        if (id != null) {
            for (Course course : courses) {
                if (course.id() == id) {
                    return course;
                }
            }
        }

        String normalized = CourseNameMatcher.normalize(reference);
        if (normalized.isEmpty()) {
            throw new AiException("课程参数不能为空");
        }
        List<Course> exact = new ArrayList<>();
        for (Course course : courses) {
            if (CourseNameMatcher.normalize(course.name()).equals(normalized)) {
                exact.add(course);
            }
        }
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.size() > 1) {
            throw new AiException("课程「" + reference + "」匹配到多门课程，请改用课程 ID："
                    + courseListText(exact));
        }

        List<Course> similar = new ArrayList<>();
        for (Course course : courses) {
            if (CourseNameMatcher.similarity(reference, course.name()) >= SIMILAR_THRESHOLD) {
                similar.add(course);
            }
        }
        if (!similar.isEmpty()) {
            similar.sort(Comparator.comparingDouble(
                    (Course course) -> CourseNameMatcher.similarity(reference, course.name()))
                    .reversed());
            List<Course> top = similar.size() <= 3 ? similar
                    : new ArrayList<>(similar.subList(0, 3));
            throw new AiException("没有找到课程「" + reference + "」。你可能是指："
                    + courseListText(top) + "。请使用完整课程名或课程 ID。");
        }
        throw new AiException("课程「" + reference + "」不存在。请先调用 list_courses 查看已有课程，"
                + "必要时再用 create_course 新建。");
    }

    private static Long parseId(String reference) {
        if (reference == null) {
            return null;
        }
        try {
            return Long.parseLong(reference.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Assignment findAssignment(String title, String courseReference) throws AiException {
        Course course = courseReference == null || courseReference.isBlank()
                ? null : findCourse(courseReference);
        List<Assignment> matches = new ArrayList<>();
        for (Assignment assignment : assignmentService.listAll()) {
            if (!assignment.title().equalsIgnoreCase(title)) {
                continue;
            }
            if (course != null && assignment.courseId() != course.id()) {
                continue;
            }
            matches.add(assignment);
        }
        if (matches.isEmpty()) {
            throw new AiException("没有找到作业「" + title + "」");
        }
        if (matches.size() > 1) {
            throw new AiException("找到多条同名作业，请指定课程（course 参数，可用课程名或 ID）");
        }
        return matches.get(0);
    }

    private Map<Long, String> courseNames() {
        Map<Long, String> names = new HashMap<>();
        for (Course course : courseService.list()) {
            names.put(course.id(), course.name());
        }
        return names;
    }

    private static String required(JsonObject args, String key) throws AiException {
        String value = optionalText(args, key);
        if (value == null || value.isBlank()) {
            throw new AiException("缺少参数：" + key);
        }
        return value;
    }

    private static String optionalText(JsonObject args, String key) {
        JsonElement element = args.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString().trim();
    }

    private static String text(JsonObject args, String key) {
        String value = optionalText(args, key);
        return value == null ? "?" : value;
    }

    private static double number(JsonObject args, String key, double fallback) {
        JsonElement element = args.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsDouble();
    }

    private static LocalDateTime parseDue(String value) throws AiException {
        try {
            return Formatters.parseUserDateTime(value);
        } catch (IllegalArgumentException e) {
            throw new AiException("时间格式应为 yyyy-MM-dd HH:mm，收到：" + value);
        }
    }

    private static AssignmentStatus parseStatus(String value) throws AiException {
        try {
            return AssignmentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AiException("未知状态：" + value + "（可用 TODO / DOING / DONE / SUBMITTED）");
        }
    }

    /** 确认框里的定位信息：有课程就带上课程名。 */
    private static String label(JsonObject args) {
        String course = optionalText(args, "course");
        String title = text(args, "title");
        return course == null || course.isBlank() ? title : course + " · " + title;
    }

    /** 确认框里列出"改成什么"，而不是只列字段名，方便用户判断 Agent 是否理解正确。 */
    private static String updateDetails(JsonObject args) {
        List<String> parts = new ArrayList<>();
        if (args.has("newTitle") && !args.get("newTitle").isJsonNull()) {
            parts.add("标题改为 " + args.get("newTitle").getAsString());
        }
        if (args.has("dueAt") && !args.get("dueAt").isJsonNull()) {
            parts.add("截止改为 " + formatDueText(args.get("dueAt").getAsString()));
        }
        if (args.has("weight") && !args.get("weight").isJsonNull()) {
            parts.add("权重改为 " + formatNumber(args.get("weight")) + "%");
        }
        if (args.has("estimatedMinutes") && !args.get("estimatedMinutes").isJsonNull()) {
            parts.add("预计耗时改为 " + args.get("estimatedMinutes").getAsInt() + " 分钟");
        }
        if (args.has("status") && !args.get("status").isJsonNull()) {
            parts.add("状态改为 " + statusLabel(args.get("status").getAsString()));
        }
        if (args.has("score") && !args.get("score").isJsonNull()) {
            parts.add("得分改为 " + formatNumber(args.get("score")));
        }
        if (args.has("notes") && !args.get("notes").isJsonNull()) {
            parts.add("备注改为 " + truncate(args.get("notes").getAsString(), 24));
        }
        return parts.isEmpty() ? "未指定字段" : String.join("，", parts);
    }

    private static String formatDueText(String value) {
        try {
            return Formatters.display(Formatters.parseUserDateTime(value));
        } catch (RuntimeException e) {
            return value;
        }
    }

    private static String statusLabel(String value) {
        try {
            return AssignmentStatus.valueOf(value.trim().toUpperCase()).label();
        } catch (RuntimeException e) {
            return value;
        }
    }

    private static String formatNumber(JsonElement element) {
        double value = element.getAsDouble();
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }
}
