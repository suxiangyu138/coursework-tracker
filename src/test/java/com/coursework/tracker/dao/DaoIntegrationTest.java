package com.coursework.tracker.dao;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.FiredKey;
import com.coursework.tracker.model.ReminderRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaoIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 14, 25, 0);

    private Database database;
    private CourseDao courseDao;
    private AssignmentDao assignmentDao;
    private ReminderLogDao reminderLogDao;
    private SettingDao settingDao;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        SchemaMigrator.migrate(database);
        courseDao = new CourseDao(database);
        assignmentDao = new AssignmentDao(database);
        reminderLogDao = new ReminderLogDao(database);
        settingDao = new SettingDao(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private Course createCourse(String name) {
        Course draft = Course.draft(name, null, 3.0, null, 90.0, NOW);
        return draft.withId(courseDao.insert(draft));
    }

    private Assignment createAssignment(Course course, String title, LocalDateTime dueAt,
                                        AssignmentStatus status) {
        Assignment draft = new Assignment(0L, course.id(), title, dueAt, 20, 120, status,
                null, null, null, NOW, NOW);
        return draft.withId(assignmentDao.insert(draft));
    }

    @Test
    @DisplayName("初始化建表与版本号")
    void schemaCreated() {
        long tables = database.queryLong(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN "
                        + "('course','assignment','reminder_log','setting')");
        assertEquals(4, tables);
        assertEquals("2", settingDao.get("schema.version", null));
    }

    @Test
    @DisplayName("课程增删改查")
    void courseCrud() {
        Course created = createCourse("操作系统");
        assertTrue(created.id() > 0);
        assertEquals("操作系统", courseDao.findById(created.id()).orElseThrow().name());

        Course updated = created.withArchived(false);
        courseDao.update(updated);

        assertTrue(courseDao.findByName("操作系统").isPresent());
        courseDao.delete(created.id());
        assertTrue(courseDao.findById(created.id()).isEmpty());
    }

    @Test
    @DisplayName("课程名唯一约束")
    void duplicateCourseName() {
        createCourse("数据库原理");
        Course duplicate = Course.draft("数据库原理", null, 3.0, null, null, NOW);
        assertThrows(DataAccessException.class, () -> courseDao.insert(duplicate));
    }

    @Test
    @DisplayName("作业增删改查与按截止时间排序")
    void assignmentCrud() {
        Course course = createCourse("操作系统");
        createAssignment(course, "晚", NOW.plusDays(5), AssignmentStatus.TODO);
        Assignment early = createAssignment(course, "早", NOW.plusDays(1), AssignmentStatus.TODO);

        List<Assignment> all = assignmentDao.findAll();
        assertEquals(2, all.size());
        assertEquals(early.id(), all.get(0).id());

        Assignment changed = all.get(0).withStatus(AssignmentStatus.DONE);
        assignmentDao.update(changed);
        assertEquals(AssignmentStatus.DONE,
                assignmentDao.findById(changed.id()).orElseThrow().status());

        assignmentDao.delete(changed.id());
        assertEquals(1, assignmentDao.findAll().size());
    }

    @Test
    @DisplayName("只查未完成且在视野内的作业")
    void activeAssignments() {
        Course course = createCourse("操作系统");
        createAssignment(course, "逾期", NOW.minusDays(1), AssignmentStatus.TODO);
        createAssignment(course, "进行中", NOW.plusHours(10), AssignmentStatus.DOING);
        createAssignment(course, "已完成", NOW.plusHours(2), AssignmentStatus.DONE);
        createAssignment(course, "太远", NOW.plusDays(30), AssignmentStatus.TODO);

        List<Assignment> active = assignmentDao.findActiveUntil(NOW.plusDays(7));
        assertEquals(2, active.size());
        assertEquals("逾期", active.get(0).title());
    }

    @Test
    @DisplayName("删除课程级联删除作业与提醒记录")
    void cascadeDelete() {
        Course course = createCourse("操作系统");
        Assignment assignment = createAssignment(course, "实验一", NOW.plusDays(1),
                AssignmentStatus.TODO);
        reminderLogDao.insertIgnore(assignment.id(), ReminderRule.T24H, NOW);

        assertTrue(reminderLogDao.countAll() > 0);
        courseDao.delete(course.id());
        assertEquals(0, assignmentDao.findAll().size());
        assertEquals(0, reminderLogDao.countAll());
    }

    @Test
    @DisplayName("提醒记录唯一约束与批量查询")
    void reminderDedupe() {
        Course course = createCourse("操作系统");
        Assignment assignment = createAssignment(course, "实验一", NOW.plusDays(1),
                AssignmentStatus.TODO);

        assertTrue(reminderLogDao.insertIgnore(assignment.id(), ReminderRule.T24H, NOW));
        assertFalse(reminderLogDao.insertIgnore(assignment.id(), ReminderRule.T24H, NOW.plusHours(1)));

        Map<FiredKey, LocalDateTime> fired = reminderLogDao.findFired(List.of(assignment.id()));
        assertEquals(1, fired.size());
        assertEquals(NOW, fired.get(FiredKey.of(assignment.id(), ReminderRule.T24H)));
    }

    @Test
    @DisplayName("设置项读写与默认值")
    void settings() {
        assertEquals("23:00", settingDao.get("reminder.quiet.start", "23:00"));
        settingDao.set("theme", "dark");
        assertEquals("dark", settingDao.get("theme", "light"));
        assertTrue(settingDao.getBoolean("reminder.enabled", true));
        assertEquals(168, settingDao.getInt("urgency.horizonHours", 0));
    }

    @Test
    @DisplayName("事务失败整体回滚")
    void transactionRollback() {
        assertThrows(IllegalStateException.class, () -> database.transaction(connection -> {
            try (var ps = connection.prepareStatement(
                    "INSERT INTO course(name, credit, archived, created_at) VALUES('临时', 1, 0, '2026-10-18 14:25:00')")) {
                ps.executeUpdate();
            }
            throw new IllegalStateException("模拟失败");
        }));
        assertEquals(0, courseDao.findAll(true).size());
    }

    @Test
    @DisplayName("特殊字符与注入字符串按普通文本存储")
    void specialCharacters() {
        String title = "作业 \"引号\" 😀";
        String notes = "' OR 1=1 --";
        Course course = createCourse("测试课程");
        Assignment draft = new Assignment(0L, course.id(), title, NOW.plusDays(1), 10, null,
                AssignmentStatus.TODO, null, null, notes, NOW, NOW);
        long id = assignmentDao.insert(draft);

        Assignment loaded = assignmentDao.findById(id).orElseThrow();
        assertEquals(title, loaded.title());
        assertEquals(notes, loaded.notes());
    }

    @Test
    @DisplayName("跨年时间字符串排序正确")
    void timeOrdering() {
        Course course = createCourse("操作系统");
        createAssignment(course, "明年", LocalDateTime.of(2027, 1, 1, 0, 0), AssignmentStatus.TODO);
        createAssignment(course, "今年底", LocalDateTime.of(2026, 12, 31, 23, 59), AssignmentStatus.TODO);
        createAssignment(course, "年中", LocalDateTime.of(2026, 6, 1, 0, 0), AssignmentStatus.TODO);

        List<Assignment> all = assignmentDao.findAll();
        assertEquals("年中", all.get(0).title());
        assertEquals("今年底", all.get(1).title());
        assertEquals("明年", all.get(2).title());
    }

    @Test
    @DisplayName("迁移可重复执行")
    void migrationIdempotent() {
        SchemaMigrator.migrate(database);
        SchemaMigrator.migrate(database);
        Course course = createCourse("重复迁移");
        assertNotNull(course.id());
    }
}
