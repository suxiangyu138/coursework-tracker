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
    public static final String CREATE_ASSIGNMENT = "create_assignment";
    public static final String UPDATE_ASSIGNMENT = "update_assignment";
    public static final String DELETE_ASSIGNMENT = "delete_assignment";
    public static final String LIST_ASSIGNMENTS = "list_assignments";

    private AiTools() {
    }

    public static List<DeepSeekClient.ToolSpec> specs() {
        List<DeepSeekClient.ToolSpec> tools = new ArrayList<>();

        JsonObject courseProps = new JsonObject();
        courseProps.add("name", stringProp("课程名称，必须与已有课程区分，例如「数据结构」"));
        courseProps.add("credit", numberProp("学分，0–20，默认 3"));
        courseProps.add("teacher", stringProp("任课教师，可省略"));
        courseProps.add("targetScore", numberProp("目标总评分 1–100，可省略"));
        tools.add(new DeepSeekClient.ToolSpec(CREATE_COURSE,
                "新建一门课程。作业只能挂在已有课程下，课程不存在时先调用它。",
                schema(courseProps, "name")));

        JsonObject assignmentProps = new JsonObject();
        assignmentProps.add("course", stringProp("课程名称，必须是已有课程"));
        assignmentProps.add("title", stringProp("作业标题，例如「实验三 内存管理」"));
        assignmentProps.add("dueAt", stringProp("截止时间，格式 yyyy-MM-dd HH:mm"));
        assignmentProps.add("weight", numberProp("作业权重，占课程总评的百分比 0–100，默认 0"));
        assignmentProps.add("estimatedMinutes", numberProp("预计耗时（分钟），可省略"));
        assignmentProps.add("notes", stringProp("备注，可省略"));
        tools.add(new DeepSeekClient.ToolSpec(CREATE_ASSIGNMENT,
                "新建一条作业。用户说「下周三交数据库大作业，40%」时使用，先确认课程存在。",
                schema(assignmentProps, "course", "title", "dueAt")));

        JsonObject updateProps = new JsonObject();
        updateProps.add("course", stringProp("课程名称，用于定位同名作业，建议提供"));
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
        deleteProps.add("course", stringProp("课程名称，用于定位同名作业，建议提供"));
        deleteProps.add("title", stringProp("要删除的作业标题"));
        tools.add(new DeepSeekClient.ToolSpec(DELETE_ASSIGNMENT,
                "删除一条作业（不可恢复）。用户明确要求删除时才调用。",
                schema(deleteProps, "title")));

        JsonObject listProps = new JsonObject();
        listProps.add("course", stringProp("按课程筛选，可省略"));
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
