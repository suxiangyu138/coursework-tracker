package com.coursework.tracker.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 可用的工具定义。保持精简：课程与作业的增改删查，刚好覆盖"用自然语言管作业"。
 */
public final class AiTools {

    public static final String CREATE_COURSE = "create_course";
    public static final String UPDATE_COURSE = "update_course";
    public static final String DELETE_COURSE = "delete_course";
    public static final String LIST_COURSES = "list_courses";
    public static final String CREATE_ASSIGNMENT = "create_assignment";
    public static final String UPDATE_ASSIGNMENT = "update_assignment";
    public static final String DELETE_ASSIGNMENT = "delete_assignment";
    public static final String BATCH_UPDATE_ASSIGNMENTS = "batch_update_assignments";
    public static final String LIST_ASSIGNMENTS = "list_assignments";

    private AiTools() {
    }

    public static List<DeepSeekClient.ToolSpec> specs() {
        List<DeepSeekClient.ToolSpec> tools = new ArrayList<>();

        JsonObject courseProps = new JsonObject();
        courseProps.add("name", stringProp("课程名称，必须与已有课程区分，例如「数据结构」"));
        courseProps.add("semester", stringProp(
                "学期编码，例如 2026-2027-1（2026–2027 学年第一学期/秋季）；省略时使用当前学期"));
        courseProps.add("credit", numberProp("学分，0–20，默认 3"));
        courseProps.add("teacher", stringProp("任课教师，可省略"));
        courseProps.add("targetScore", numberProp("目标总评分 1–100，可省略"));
        courseProps.add("forceNew", booleanProp(
                "仅在用户明确确认这是另一门课、不是已有相似课程时设为 true"));
        tools.add(new DeepSeekClient.ToolSpec(CREATE_COURSE,
                "新建一门课程。调用前必须先查 list_courses：已有同名或高度相似的课程时直接复用，不要重复创建。"
                        + "工具也会自动拦截重复/相似课程。",
                schema(courseProps, "name")));

        JsonObject updateCourseProps = new JsonObject();
        updateCourseProps.add("course", stringProp("要修改的课程名称或课程 ID，可从 list_courses 获取"));
        updateCourseProps.add("newName", stringProp("新课程名，可省略"));
        updateCourseProps.add("semester", stringProp(
                "新学期编码，例如 2027-2028-1；传空字符串表示「未分学期」，可省略"));
        updateCourseProps.add("credit", numberProp("新学分 0–20，可省略"));
        updateCourseProps.add("teacher", stringProp("新教师，传空字符串表示清空，可省略"));
        updateCourseProps.add("targetScore", numberProp("新目标分 1–100，传 0 表示清空，可省略"));
        tools.add(new DeepSeekClient.ToolSpec(UPDATE_COURSE,
                "修改已有课程的课程名、学期、学分、教师或目标分。至少提供一个新值。",
                schema(updateCourseProps, "course")));

        JsonObject deleteCourseProps = new JsonObject();
        deleteCourseProps.add("course", stringProp("要删除的课程名称或课程 ID"));
        tools.add(new DeepSeekClient.ToolSpec(DELETE_COURSE,
                "删除一门课程（不可恢复）。该课程下的作业和提醒记录会级联删除，"
                        + "执行前会显示作业数量并自动备份数据库。",
                schema(deleteCourseProps, "course")));

        JsonObject listCoursesProps = new JsonObject();
        listCoursesProps.add("semester", stringProp(
                "只看某个学期，例如 2026-2027-1；传「未分学期」表示未分配学期的课程，可省略"));
        listCoursesProps.add("keyword", stringProp("按课程名/教师模糊筛选，可省略"));
        tools.add(new DeepSeekClient.ToolSpec(LIST_COURSES,
                "查询课程列表（含 ID、学期、学分、教师、作业数量）。"
                        + "新建课程、删除课程、或用户问「我有哪些课」之前都应先调用它。",
                schema(listCoursesProps)));

        JsonObject assignmentProps = new JsonObject();
        assignmentProps.add("course", stringProp("课程名称或课程 ID（必须是已有课程）"));
        assignmentProps.add("title", stringProp("作业标题，例如「实验三 内存管理」"));
        assignmentProps.add("dueAt", stringProp("截止时间，格式 yyyy-MM-dd HH:mm"));
        assignmentProps.add("weight", numberProp("作业权重，占课程总评的百分比 0–100，默认 0"));
        assignmentProps.add("estimatedMinutes", numberProp("预计耗时（分钟），可省略"));
        assignmentProps.add("notes", stringProp("备注，可省略"));
        tools.add(new DeepSeekClient.ToolSpec(CREATE_ASSIGNMENT,
                "新建一条作业。用户说「下周三交数据库大作业，40%」时使用，先确认课程存在。",
                schema(assignmentProps, "course", "title", "dueAt")));

        JsonObject updateProps = new JsonObject();
        updateProps.add("course", stringProp("课程名称或课程 ID，用于定位同名作业，建议提供"));
        updateProps.add("title", stringProp("要修改的作业标题（原值）"));
        updateProps.add("newTitle", stringProp("新标题，可省略"));
        updateProps.add("dueAt", stringProp("新截止时间，格式 yyyy-MM-dd HH:mm，可省略"));
        updateProps.add("weight", numberProp("新权重 0–100，可省略"));
        updateProps.add("estimatedMinutes", numberProp("新预计耗时（分钟），可省略"));
        updateProps.add("status", statusProp());
        updateProps.add("score", numberProp("得分 0–100，可省略"));
        updateProps.add("notes", stringProp("新备注，可省略"));
        tools.add(new DeepSeekClient.ToolSpec(UPDATE_ASSIGNMENT,
                "修改已有作业的标题、截止时间、权重、状态、得分或备注。至少提供一个新值。",
                schema(updateProps, "title")));

        JsonObject deleteProps = new JsonObject();
        deleteProps.add("course", stringProp("课程名称或课程 ID，用于定位同名作业，建议提供"));
        deleteProps.add("title", stringProp("要删除的作业标题"));
        tools.add(new DeepSeekClient.ToolSpec(DELETE_ASSIGNMENT,
                "删除一条作业（不可恢复）。用户明确要求删除时才调用。",
                schema(deleteProps, "title")));

        JsonObject batchProps = new JsonObject();
        batchProps.add("course", stringProp("只处理该课程（名称或 ID），可省略"));
        batchProps.add("status", statusProp());
        batchProps.add("unfinished", booleanProp("只处理未完成（未开始 / 进行中）的作业"));
        batchProps.add("from", stringProp("截止时间下限 yyyy-MM-dd HH:mm，可省略"));
        batchProps.add("to", stringProp("截止时间上限 yyyy-MM-dd HH:mm，可省略"));
        batchProps.add("postponeDays", numberProp("命中的作业截止时间整体推迟 N 天，负数为提前"));
        batchProps.add("setDueAt", stringProp("命中的作业截止时间统一改为该时间 yyyy-MM-dd HH:mm"));
        batchProps.add("setStatus", statusProp());
        tools.add(new DeepSeekClient.ToolSpec(BATCH_UPDATE_ASSIGNMENTS,
                "批量修改作业，用于「把这周未完成的作业推迟三天」「把这门课进行中的作业标记为未开始」等请求。"
                        + "必须至少提供一个筛选条件（course / status / unfinished / from / to），"
                        + "并且只能提供一个改动（postponeDays / setDueAt / setStatus）。",
                schema(batchProps)));

        JsonObject listProps = new JsonObject();
        listProps.add("course", stringProp("按课程筛选（名称或 ID），可省略"));
        listProps.add("status", statusProp());
        listProps.add("withinDays", numberProp("只看未来 N 天内到期的作业，可省略"));
        tools.add(new DeepSeekClient.ToolSpec(LIST_ASSIGNMENTS,
                "查询作业列表，用于回答「这周还有什么作业」「数据库课还有哪些没做」等问题。",
                schema(listProps)));

        return tools;
    }

    private static JsonObject schema(JsonObject properties, String... required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        if (required.length > 0) {
            JsonArray array = new JsonArray();
            for (String field : required) {
                array.add(field);
            }
            schema.add("required", array);
        }
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject stringProp(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        return property;
    }

    private static JsonObject numberProp(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "number");
        property.addProperty("description", description);
        return property;
    }

    private static JsonObject booleanProp(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "boolean");
        property.addProperty("description", description);
        return property;
    }

    private static JsonObject statusProp() {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", "作业状态");
        JsonArray values = new JsonArray();
        values.add("TODO");
        values.add("DOING");
        values.add("DONE");
        values.add("SUBMITTED");
        property.add("enum", values);
        return property;
    }
}
