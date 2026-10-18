package com.coursework.tracker.ai;

import com.coursework.tracker.core.EventBus;
import com.coursework.tracker.dao.AssignmentDao;
import com.coursework.tracker.dao.CourseDao;
import com.coursework.tracker.dao.Database;
import com.coursework.tracker.dao.ReminderLogDao;
import com.coursework.tracker.dao.SchemaMigrator;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.service.AssignmentService;
import com.coursework.tracker.service.CourseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiActionExecutorTest {

    private Database database;
    private CourseService courseService;
    private AssignmentService assignmentService;
    private AiActionExecutor executor;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        SchemaMigrator.migrate(database);
        EventBus eventBus = new EventBus();
        CourseDao courseDao = new CourseDao(database);
        AssignmentDao assignmentDao = new AssignmentDao(database);
        ReminderLogDao reminderLogDao = new ReminderLogDao(database);
        courseService = new CourseService(courseDao, eventBus);
        assignmentService = new AssignmentService(assignmentDao, courseDao, reminderLogDao, eventBus);
        executor = new AiActionExecutor(courseService, assignmentService);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private String execute(String tool, String arguments) throws AiException {
        return executor.execute(new DeepSeekClient.ToolCall("call-1", tool, arguments));
    }

    @Test
    @DisplayName("自然语言工具链：建课程 → 建作业 → 改状态 → 查询 → 删除")
    void fullToolChain() throws AiException {
        String courseResult = execute(AiTools.CREATE_COURSE,
                "{\"name\":\"数据结构\",\"credit\":3}");
        assertTrue(courseResult.contains("数据结构"));
        List<Course> courses = courseService.list();
        assertEquals(1, courses.size());

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
    }

    @Test
    @DisplayName("只有查询工具是只读的")
    void mutatingFlags() {
        assertTrue(executor.isMutating(AiTools.CREATE_ASSIGNMENT));
        assertTrue(executor.isMutating(AiTools.DELETE_ASSIGNMENT));
        assertTrue(executor.isMutating(AiTools.UPDATE_ASSIGNMENT));
        assertTrue(!executor.isMutating(AiTools.LIST_ASSIGNMENTS));
    }
}
