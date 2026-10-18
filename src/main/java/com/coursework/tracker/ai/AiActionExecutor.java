package com.coursework.tracker.ai;

import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.service.AssignmentService;
import com.coursework.tracker.service.BusinessException;
import com.coursework.tracker.service.CourseService;
import com.coursework.tracker.util.Formatters;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行 AI 工具调用。所有写操作由调用方先做用户确认；这里只负责解析参数、调用 Service、返回可读结果。
 */
public final class AiActionExecutor {

    private final CourseService courseService;
    private final AssignmentService assignmentService;

    public AiActionExecutor(CourseService courseService, AssignmentService assignmentService) {
        this.courseService = courseService;
        this.assignmentService = assignmentService;
    }

    public boolean isMutating(String toolName) {
        return !AiTools.LIST_ASSIGNMENTS.equals(toolName);
    }

    /** 给确认对话框展示的人类可读描述。 */
    public String preview(DeepSeekClient.ToolCall call) {
        try {
            JsonObject args = args(call);
            return switch (call.name()) {
                case AiTools.CREATE_COURSE -> "新建课程：" + text(args, "name")
                        + "（" + number(args, "credit", 3) + " 学分）";
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
        double credit = number(args, "credit", 3);
        String teacher = optionalText(args, "teacher");
        Double target = args.has("targetScore") && !args.get("targetScore").isJsonNull()
                ? args.get("targetScore").getAsDouble() : null;
        Course course = courseService.create(name, null, credit, teacher, target);
        return "已新建课程：" + course.name() + "（" + course.credit() + " 学分）";
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
        String courseFilter = optionalText(args, "course");
        String statusFilter = optionalText(args, "status");
        boolean unfinished = bool(args, "unfinished");
        LocalDateTime from = optionalDate(args, "from");
        LocalDateTime to = optionalDate(args, "to");

        if (courseFilter == null && statusFilter == null && !unfinished && from == null && to == null) {
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

        AssignmentStatus status = statusFilter == null ? null : parseStatus(statusFilter);
        Map<Long, String> courseNames = courseNames();
        List<Assignment> matches = new ArrayList<>();
        for (Assignment assignment : assignmentService.listAll()) {
            String courseName = courseNames.getOrDefault(assignment.courseId(), "未分类");
            if (courseFilter != null && !courseFilter.equalsIgnoreCase(courseName)) {
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
        String courseFilter = optionalText(args, "course");
        String statusFilter = optionalText(args, "status");
        Integer withinDays = args.has("withinDays") && !args.get("withinDays").isJsonNull()
                ? args.get("withinDays").getAsInt() : null;

        Map<Long, String> courseNames = courseNames();
        AssignmentStatus status = statusFilter == null ? null : parseStatus(statusFilter);
        LocalDateTime now = ClockProvider.now();
        LocalDateTime limit = withinDays == null ? null : now.plusDays(withinDays);

        List<Assignment> matches = new ArrayList<>();
        for (Assignment assignment : assignmentService.listAll()) {
            String courseName = courseNames.getOrDefault(assignment.courseId(), "未分类");
            if (courseFilter != null && !courseFilter.equalsIgnoreCase(courseName)) {
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

    private Course findCourse(String name) throws AiException {
        for (Course course : courseService.list()) {
            if (course.name().equalsIgnoreCase(name)) {
                return course;
            }
        }
        throw new AiException("课程「" + name + "」不存在，请先新建课程");
    }

    private Assignment findAssignment(String title, String courseName) throws AiException {
        Map<Long, String> courseNames = courseNames();
        List<Assignment> matches = new ArrayList<>();
        for (Assignment assignment : assignmentService.listAll()) {
            if (!assignment.title().equalsIgnoreCase(title)) {
                continue;
            }
            String name = courseNames.getOrDefault(assignment.courseId(), "未分类");
            if (courseName != null && !courseName.equalsIgnoreCase(name)) {
                continue;
            }
            matches.add(assignment);
        }
        if (matches.isEmpty()) {
            throw new AiException("没有找到作业「" + title + "」");
        }
        if (matches.size() > 1) {
            throw new AiException("找到多条同名作业，请指定课程（course 参数）");
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
