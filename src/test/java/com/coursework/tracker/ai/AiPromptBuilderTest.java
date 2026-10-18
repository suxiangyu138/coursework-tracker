package com.coursework.tracker.ai;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.Course;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiPromptBuilderTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("提示词包含当前任务快照、课程进度和规划规则")
    void promptContainsTaskContext() {
        try (AppContext context = AppContext.open(temp.resolve("prompt.db"))) {
            Course course = context.courseService().create("数据库原理", null, 3.0, "张老师",
                    "2026-2027-1", 90.0);
            context.assignmentService().create(Assignment.draft(course.id(), "大作业 图书管理系统",
                    LocalDateTime.now().plusDays(2), 40, 240, "重点", LocalDateTime.now()));
            context.assignmentService().create(Assignment.draft(course.id(), "章节练习",
                    LocalDateTime.now().plusDays(6), 10, 30, null, LocalDateTime.now()));

            String prompt = AiPromptBuilder.build(context);

            assertTrue(prompt.contains("【任务状态快照】"));
            assertTrue(prompt.contains("数据库原理"));
            assertTrue(prompt.contains("大作业 图书管理系统"));
            assertTrue(prompt.contains("优先任务"));
            assertTrue(prompt.contains("【行为规则】"));
            assertTrue(prompt.contains("今天先做什么"));
            assertTrue(prompt.contains("19:00–23:00"));
            assertTrue(prompt.contains("课程名可能因为空格"));
        }
    }

    @Test
    @DisplayName("空数据提示词不会编造课程或作业")
    void emptyPrompt() {
        try (AppContext context = AppContext.open(temp.resolve("empty.db"))) {
            String prompt = AiPromptBuilder.build(context);

            assertTrue(prompt.contains("还没有作业数据"));
            assertTrue(prompt.contains("暂无未完成作业"));
            assertTrue(prompt.contains("暂无课程"));
            assertFalse(prompt.contains("=========="));
        }
    }
}
