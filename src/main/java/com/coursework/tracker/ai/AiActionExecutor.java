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
                        + text(args, "title") + "，截止 " + text(args, "dueAt")
                        + "，权重 " + number(args, "weight", 0) + "%";
                case AiTools.UPDATE_ASSIGNMENT -> "修改作业：" + text(args, "title")
                        + "（" + changedFields(args) + "）";
                case AiTools.DELETE_ASSIGNMENT -> "删除作业：" + text(args, "title");
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

    private static String changedFields(JsonObject args) {
        List<String> fields = new ArrayList<>();
        for (String field : new String[]{"newTitle", "dueAt", "weight", "estimatedMinutes",
                "status", "score", "notes"}) {
            if (args.has(field) && !args.get(field).isJsonNull()) {
                fields.add(field);
            }
        }
        return fields.isEmpty() ? "未指定字段" : String.join(", ", fields);
    }
}
