package com.coursework.tracker.ai;

import com.coursework.tracker.core.EventBus;
import com.coursework.tracker.dao.AssignmentDao;
import com.coursework.tracker.dao.CourseDao;
import com.coursework.tracker.dao.Database;
import com.coursework.tracker.dao.ReminderLogDao;
import com.coursework.tracker.dao.SchemaMigrator;
import com.coursework.tracker.dao.SettingDao;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.service.AssignmentService;
import com.coursework.tracker.service.BackupService;
import com.coursework.tracker.service.BusinessException;
import com.coursework.tracker.service.CourseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiActionExecutorTest {

    @TempDir
    Path temp;

    private Database database;
    private CourseService courseService;
    private AssignmentService assignmentService;
    private AiActionExecutor executor;

    @BeforeEach
    void setUp() {
        database = Database.open(temp.resolve("ai.db"));
        SchemaMigrator.migrate(database);
        EventBus eventBus = new EventBus();
        CourseDao courseDao = new CourseDao(database);
        AssignmentDao assignmentDao = new AssignmentDao(database);
        ReminderLogDao reminderLogDao = new ReminderLogDao(database);
        SettingDao settingDao = new SettingDao(database);
        courseService = new CourseService(courseDao, eventBus);
        assignmentService = new AssignmentService(assignmentDao, courseDao, reminderLogDao, eventBus);
        BackupService backupService = new BackupService(database, courseDao, assignmentDao,
                settingDao, temp.resolve("backup"));
        executor = new AiActionExecutor(courseService, assignmentService, backupService);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private String execute(String tool, String arguments) throws AiException {
        return executor.execute(new DeepSeekClient.ToolCall("call-1", tool, arguments));
    }

    @Test
    @DisplayName("批量推迟：只影响筛选出的未完成作业")
    void batchPostpone() throws AiException {
        LocalDateTime now = LocalDateTime.of(2026, 10, 18, 12, 0);
        Course course = courseService.create("操作系统", null, 4, null, null, null);
        assignmentService.create(Assignment.draft(course.id(), "实验一",
                LocalDateTime.of(2026, 10, 20, 23, 59), 15, null, null, now));
        assignmentService.create(Assignment.draft(course.id(), "实验二",
                LocalDateTime.of(2026, 10, 22, 23, 59), 15, null, null, now));
        assignmentService.create(Assignment.draft(course.id(), "期末大作业",
                LocalDateTime.of(2026, 11, 10, 23, 59), 40, null, null, now));
        Assignment done = assignmentService.create(Assignment.draft(course.id(), "已完成作业",
                LocalDateTime.of(2026, 10, 21, 23, 59), 10, null, null, now));
        assignmentService.update(done.withStatus(AssignmentStatus.DONE).withSubmittedAt(now));

        String preview = executor.preview(new DeepSeekClient.ToolCall("1",
                AiTools.BATCH_UPDATE_ASSIGNMENTS,
                "{\"course\":\"操作系统\",\"unfinished\":true,\"from\":\"2026-10-19 00:00\","
                        + "\"to\":\"2026-10-25 23:59\",\"postponeDays\":3}"));
        assertTrue(preview.contains("批量修改作业"));
        assertTrue(preview.contains("命中 2 条"));
        assertTrue(preview.contains("截止推迟 3 天"));

        String result = execute(AiTools.BATCH_UPDATE_ASSIGNMENTS,
                "{\"course\":\"操作系统\",\"unfinished\":true,\"from\":\"2026-10-19 00:00\","
                        + "\"to\":\"2026-10-25 23:59\",\"postponeDays\":3}");
        assertTrue(result.contains("已批量更新 2 条"));

        assertEquals(LocalDateTime.of(2026, 10, 23, 23, 59), byTitle("实验一").dueAt());
        assertEquals(LocalDateTime.of(2026, 10, 25, 23, 59), byTitle("实验二").dueAt());
        assertEquals(LocalDateTime.of(2026, 11, 10, 23, 59), byTitle("期末大作业").dueAt());
        assertEquals(LocalDateTime.of(2026, 10, 21, 23, 59), byTitle("已完成作业").dueAt());
    }

    @Test
    @DisplayName("批量修改必须提供筛选条件与唯一改动")
    void batchValidation() {
        AiException noFilter = assertThrows(AiException.class, () -> execute(
                AiTools.BATCH_UPDATE_ASSIGNMENTS, "{\"postponeDays\":1}"));
        assertTrue(noFilter.getMessage().contains("筛选条件"));

        AiException twoChanges = assertThrows(AiException.class, () -> execute(
                AiTools.BATCH_UPDATE_ASSIGNMENTS,
                "{\"course\":\"操作系统\",\"postponeDays\":1,\"setStatus\":\"DONE\"}"));
        assertTrue(twoChanges.getMessage().contains("只能提供"));
    }

    private Assignment byTitle(String title) {
        return assignmentService.listAll().stream()
                .filter(assignment -> assignment.title().equals(title))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("工具全链路：建课程 → 建作业 → 改状态 → 查询 → 删除")
    void fullToolChain() throws AiException {
        String courseResult = execute(AiTools.CREATE_COURSE,
                "{\"name\":\"数据结构\",\"credit\":3}");
        assertTrue(courseResult.contains("数据结构"));
        List<Course> courses = courseService.list();
        assertEquals(1, courses.size());
        assertTrue(courses.get(0).semester() != null);

        String assignmentResult = execute(AiTools.CREATE_ASSIGNMENT,
                "{\"course\":\"数据结构\",\"title\":\"实验一 链表\",\"dueAt\":\"2026-10-25 23:59\","
                        + "\"weight\":20,\"estimatedMinutes\":120}");
        assertTrue(assignmentResult.contains("实验一 链表"));

        List<Assignment> assignments = assignmentService.listAll();
        assertEquals(1, assignments.size());
        assertEquals(20.0, assignments.get(0).weight(), 0.001);

        execute(AiTools.UPDATE_ASSIGNMENT,
                "{\"course\":\"数据结构\",\"title\":\"实验一 链表\",\"status\":\"DONE\",\"score\":95}");
        Assignment updated = assignmentService.listAll().get(0);
        assertEquals(AssignmentStatus.DONE, updated.status());
        assertEquals(95.0, updated.score(), 0.001);

        String listResult = execute(AiTools.LIST_ASSIGNMENTS, "{\"course\":\"数据结构\"}");
        assertTrue(listResult.contains("实验一 链表"));
        assertTrue(listResult.contains("已完成"));

        execute(AiTools.DELETE_ASSIGNMENT, "{\"course\":\"数据结构\",\"title\":\"实验一 链表\"}");
        assertEquals(0, assignmentService.listAll().size());
    }

    @Test
    @DisplayName("课程不存在时返回可读错误")
    void missingCourse() {
        AiException error = assertThrows(AiException.class, () -> execute(AiTools.CREATE_ASSIGNMENT,
                "{\"course\":\"不存在的课\",\"title\":\"作业\",\"dueAt\":\"2026-10-25 23:59\"}"));
        assertTrue(error.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("确认框描述包含关键信息")
    void preview() {
        String text = executor.preview(new DeepSeekClient.ToolCall("1", AiTools.CREATE_ASSIGNMENT,
                "{\"course\":\"操作系统\",\"title\":\"实验三\",\"dueAt\":\"2026-10-25 23:59\",\"weight\":15}"));
        assertTrue(text.contains("新建作业"));
        assertTrue(text.contains("操作系统"));
        assertTrue(text.contains("实验三"));

        String update = executor.preview(new DeepSeekClient.ToolCall("2", AiTools.UPDATE_ASSIGNMENT,
                "{\"course\":\"操作系统\",\"title\":\"实验三\",\"dueAt\":\"2026-10-30 23:59\","
                        + "\"weight\":15,\"status\":\"DOING\"}"));
        assertTrue(update.contains("操作系统 · 实验三"));
        assertTrue(update.contains("截止改为"));
        assertTrue(update.contains("权重改为 15%"));
        assertTrue(update.contains("进行中"));
    }

    @Test
    @DisplayName("只有查询工具是只读的")
    void mutatingFlags() {
        assertTrue(executor.isMutating(AiTools.CREATE_COURSE));
        assertTrue(executor.isMutating(AiTools.UPDATE_COURSE));
        assertTrue(executor.isMutating(AiTools.DELETE_COURSE));
        assertTrue(executor.isMutating(AiTools.CREATE_ASSIGNMENT));
        assertTrue(executor.isMutating(AiTools.DELETE_ASSIGNMENT));
        assertTrue(executor.isMutating(AiTools.UPDATE_ASSIGNMENT));
        assertFalse(executor.isMutating(AiTools.LIST_ASSIGNMENTS));
        assertFalse(executor.isMutating(AiTools.LIST_COURSES));
    }

    @Test
    @DisplayName("同名课程（空格/全半角差异）自动复用，不重复创建")
    void createCourseReusesExisting() throws AiException {
        courseService.create("操作系统（实验）", null, 1.0, null, "2026-2027-1", null);

        String result = execute(AiTools.CREATE_COURSE,
                "{\"name\":\"  操作系统(实验)  \",\"credit\":3}");

        assertTrue(result.contains("已存在") || result.contains("复用"));
        assertEquals(1, courseService.list().size());
    }

    @Test
    @DisplayName("相似课程默认不创建；forceNew 才允许新建")
    void createCourseBlocksSimilar() throws AiException {
        courseService.create("数字图像处理", null, 2.0, null, "2026-2027-1", null);

        String preview = executor.preview(new DeepSeekClient.ToolCall("1", AiTools.CREATE_COURSE,
                "{\"name\":\"数字图像处理（双语）\"}"));
        assertTrue(preview.contains("相似课程"));

        String result = execute(AiTools.CREATE_COURSE,
                "{\"name\":\"数字图像处理（双语）\"}");
        assertTrue(result.contains("未创建"));
        assertTrue(result.contains("数字图像处理"));
        assertEquals(1, courseService.list().size());

        String forced = execute(AiTools.CREATE_COURSE,
                "{\"name\":\"数字图像处理（双语）\",\"forceNew\":true}");
        assertTrue(forced.contains("已新建课程"));
        assertEquals(2, courseService.list().size());
    }

    @Test
    @DisplayName("服务层拒绝归一化后同名的课程")
    void serviceRejectsNormalizedDuplicate() {
        courseService.create("数据结构", null, 3.0, null, "2026-2027-1", null);
        assertThrows(BusinessException.class, () -> courseService.create(
                "数据 结构", null, 3.0, null, "2026-2027-1", null));
    }

    @Test
    @DisplayName("修改课程：改名、归入新学期、改学分")
    void updateCourse() throws AiException {
        Course course = courseService.create("操作系统", null, 3.0, null, "2026-2027-1", null);

        String result = execute(AiTools.UPDATE_COURSE,
                "{\"course\":\"" + course.id() + "\",\"newName\":\"操作系统原理\","
                        + "\"semester\":\"2027-2028-1\",\"credit\":4}");

        assertTrue(result.contains("操作系统原理"));
        Course updated = courseService.list().get(0);
        assertEquals("操作系统原理", updated.name());
        assertEquals("2027-2028-1", updated.semester());
        assertEquals(4.0, updated.credit(), 0.001);
    }

    @Test
    @DisplayName("删除课程级联删除作业，并自动备份数据库")
    void deleteCourseCascadesAndBacksUp() throws Exception {
        Course course = courseService.create("操作系统", null, 3.0, null, "2026-2027-1", null);
        assignmentService.create(Assignment.draft(course.id(), "实验一",
                LocalDateTime.of(2026, 10, 25, 23, 59), 10, null, null,
                LocalDateTime.of(2026, 10, 18, 12, 0)));

        String result = execute(AiTools.DELETE_COURSE,
                "{\"course\":\"" + course.id() + "\"}");

        assertTrue(result.contains("已删除课程"));
        assertEquals(0, courseService.list().size());
        assertEquals(0, assignmentService.listAll().size());
        try (var files = java.nio.file.Files.list(temp.resolve("backup"))) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().endsWith(".db")));
        }
    }

    @Test
    @DisplayName("list_courses 返回学期、ID、作业数，并支持学期与关键词筛选")
    void listCourses() throws AiException {
        Course first = courseService.create("操作系统", null, 3.0, "张老师", "2026-2027-1", null);
        courseService.create("数据结构", null, 3.0, null, "2025-2026-2", null);
        assignmentService.create(Assignment.draft(first.id(), "实验一",
                LocalDateTime.of(2026, 10, 25, 23, 59), 10, null, null,
                LocalDateTime.of(2026, 10, 18, 12, 0)));

        String all = execute(AiTools.LIST_COURSES, "{}");
        assertTrue(all.contains("2026–2027 学年"));
        assertTrue(all.contains("2025–2026 学年"));
        assertTrue(all.contains("ID " + first.id()));
        assertTrue(all.contains("1 项作业"));

        String filtered = execute(AiTools.LIST_COURSES, "{\"semester\":\"2025-2026-2\"}");
        assertTrue(filtered.contains("数据结构"));
        assertFalse(filtered.contains("操作系统"));

        String keyword = execute(AiTools.LIST_COURSES, "{\"keyword\":\"张老师\"}");
        assertTrue(keyword.contains("操作系统"));
        assertFalse(keyword.contains("数据结构"));
    }

    @Test
    @DisplayName("课程名相似时列出候选而不是猜测")
    void similarCourseLookup() {
        courseService.create("操作系统实验", null, 1.0, null, "2026-2027-1", null);

        AiException error = assertThrows(AiException.class, () -> execute(AiTools.CREATE_ASSIGNMENT,
                "{\"course\":\"操作系统\",\"title\":\"实验一\",\"dueAt\":\"2026-10-25 23:59\"}"));

        assertTrue(error.getMessage().contains("你可能是指"));
        assertTrue(error.getMessage().contains("操作系统实验"));
    }
}
