package com.coursework.tracker.service;

import com.coursework.tracker.dao.AssignmentDao;
import com.coursework.tracker.dao.CourseDao;
import com.coursework.tracker.dao.Database;
import com.coursework.tracker.dao.SchemaMigrator;
import com.coursework.tracker.dao.SettingDao;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 15, 0);

    @TempDir
    Path temp;

    private Database database;
    private CourseDao courseDao;
    private AssignmentDao assignmentDao;
    private SettingDao settingDao;
    private BackupService backup;

    @BeforeEach
    void setUp() throws IOException {
        database = Database.open(temp.resolve("app.db"));
        SchemaMigrator.migrate(database);
        courseDao = new CourseDao(database);
        assignmentDao = new AssignmentDao(database);
        settingDao = new SettingDao(database);
        backup = new BackupService(database, courseDao, assignmentDao, settingDao,
                temp.resolve("backup"));

        Course course = Course.draft("数据库原理", null, 3.0, "张老师", 90.0, NOW);
        long courseId = courseDao.insert(course);
        Assignment first = new Assignment(0L, courseId, "作业一", NOW.plusDays(1), 20, 120,
                AssignmentStatus.TODO, null, null, null, NOW, NOW);
        Assignment second = new Assignment(0L, courseId, "作业二", NOW.plusDays(2), 30, null,
                AssignmentStatus.DONE, NOW, 88.0, "备注,含逗号", NOW, NOW);
        assignmentDao.insert(first);
        assignmentDao.insert(second);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName("JSON 导出包含格式标识与全部数据")
    void exportJson() throws IOException {
        Path file = temp.resolve("backup.json");
        BackupService.ExportResult result = backup.exportJson(file);

        assertEquals(1, result.courses());
        assertEquals(2, result.assignments());
        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"format\": \"coursework-tracker-backup\""));
        assertTrue(json.contains("数据库原理"));
        assertTrue(json.contains("作业二"));
    }

    @Test
    @DisplayName("JSON 导入到新库，数据与字段一致")
    void importJsonRoundTrip() throws IOException {
        Path file = temp.resolve("backup.json");
        backup.exportJson(file);

        Path otherFile = temp.resolve("target.db");
        try (Database target = Database.open(otherFile)) {
            SchemaMigrator.migrate(target);
            CourseDao targetCourses = new CourseDao(target);
            AssignmentDao targetAssignments = new AssignmentDao(target);
            BackupService targetBackup = new BackupService(target, targetCourses, targetAssignments,
                    new SettingDao(target), temp.resolve("backup2"));

            BackupService.ImportResult result = targetBackup.importJson(file);
            assertEquals(1, result.courses());
            assertEquals(2, result.assignments());
            assertNotNull(result.safetyBackup());
            assertTrue(Files.exists(result.safetyBackup()));

            assertEquals(1, targetCourses.findAll(true).size());
            assertEquals(2, targetAssignments.findAll().size());
            Assignment imported = targetAssignments.findAll().stream()
                    .filter(a -> a.title().equals("作业二")).findFirst().orElseThrow();
            assertEquals(88.0, imported.score(), 0.001);
            assertEquals(AssignmentStatus.DONE, imported.status());
            assertEquals("备注,含逗号", imported.notes());
        }
    }

    @Test
    @DisplayName("CSV 带 BOM 与表头，转义逗号")
    void exportCsv() throws IOException {
        Path file = temp.resolve("list.csv");
        backup.exportCsv(file);
        String csv = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF课程,作业,截止时间"));
        assertTrue(csv.contains("数据库原理"));
        assertTrue(csv.contains("\"备注,含逗号\""));
    }

    @Test
    @DisplayName("每日自动备份只执行一次，并清理旧文件")
    void autoBackupOncePerDay() throws IOException {
        Path first = backup.autoBackupIfNeeded(NOW);
        assertNotNull(first);
        assertTrue(Files.exists(first));

        Path second = backup.autoBackupIfNeeded(NOW.plusHours(3));
        assertNull(second);
        assertEquals("2026-10-18", settingDao.get("backup.lastDate", ""));

        Path nextDay = backup.autoBackupIfNeeded(NOW.plusDays(1));
        assertNotNull(nextDay);
        assertTrue(Files.exists(nextDay));
    }

    @Test
    @DisplayName("格式不对的备份被拒绝，原数据不受影响")
    void rejectInvalidBackup() throws IOException {
        Path file = temp.resolve("bad.json");
        Files.writeString(file, "{\"format\":\"other\",\"version\":1,\"courses\":[],\"assignments\":[]}",
                StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> backup.importJson(file));
        assertEquals(1, courseDao.findAll(true).size());
        assertEquals(2, assignmentDao.findAll().size());
    }

    @Test
    @DisplayName("导入后提醒历史清空")
    void importClearsReminderLog() throws IOException {
        Path file = temp.resolve("backup.json");
        backup.exportJson(file);

        var reminderLogDao = new com.coursework.tracker.dao.ReminderLogDao(database);
        long assignmentId = assignmentDao.findAll().get(0).id();
        reminderLogDao.insertIgnore(assignmentId, com.coursework.tracker.model.ReminderRule.T24H, NOW);
        assertTrue(reminderLogDao.countAll() > 0);

        backup.importJson(file);
        assertEquals(0, reminderLogDao.countAll());
    }
}
