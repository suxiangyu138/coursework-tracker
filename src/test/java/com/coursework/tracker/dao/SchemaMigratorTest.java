package com.coursework.tracker.dao;

import com.coursework.tracker.model.ReminderRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigratorTest {

    @Test
    @DisplayName("v1 数据库升级到 v2：保留历史记录并允许按天提醒")
    void migrateV1ToV2() {
        try (Database database = Database.inMemory()) {
            database.execute("CREATE TABLE setting (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            database.execute("CREATE TABLE assignment (id INTEGER PRIMARY KEY, title TEXT)");
            database.execute("CREATE TABLE reminder_log ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "assignment_id INTEGER NOT NULL,"
                    + "rule_code TEXT NOT NULL,"
                    + "fired_at TEXT NOT NULL,"
                    + "UNIQUE(assignment_id, rule_code))");
            database.execute("INSERT INTO setting(key, value) VALUES('schema.version', '1')");
            database.execute("INSERT INTO assignment(id, title) VALUES(1, '实验一')");
            database.execute("INSERT INTO reminder_log(assignment_id, rule_code, fired_at) "
                    + "VALUES(1, 'OVERDUE', '2026-10-16 09:00:00')");

            SchemaMigrator.migrate(database);

            assertEquals("4", new SettingDao(database).get("schema.version", ""));
            assertEquals(2, database.queryLong(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' "
                            + "AND name IN ('ai_conversation','ai_message')"));
            assertEquals(1, database.queryLong(
                    "SELECT COUNT(*) FROM reminder_log WHERE fired_date = '2026-10-16'"));

            ReminderLogDao dao = new ReminderLogDao(database);
            LocalDateTime day1 = LocalDateTime.of(2026, 10, 17, 9, 0);
            LocalDateTime day2 = LocalDateTime.of(2026, 10, 18, 9, 0);
            assertTrue(dao.insertIgnore(1, ReminderRule.OVERDUE, day1));
            assertFalse(dao.insertIgnore(1, ReminderRule.OVERDUE, day1.plusHours(2)));
            assertTrue(dao.insertIgnore(1, ReminderRule.OVERDUE, day2));
            assertEquals(3, dao.countAll());
        }
    }

    @Test
    @DisplayName("v3 数据库升级到 v4：新增学期列并按创建时间推断，原有数据不丢")
    void migrateV3ToV4() {
        try (Database database = Database.inMemory()) {
            database.execute("CREATE TABLE setting (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            database.execute("CREATE TABLE course ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL UNIQUE,"
                    + "code TEXT,"
                    + "credit REAL NOT NULL DEFAULT 0,"
                    + "teacher TEXT,"
                    + "target_score REAL,"
                    + "archived INTEGER NOT NULL DEFAULT 0,"
                    + "created_at TEXT NOT NULL)");
            database.execute("CREATE TABLE assignment (id INTEGER PRIMARY KEY, course_id INTEGER, title TEXT)");
            database.execute("INSERT INTO setting(key, value) VALUES('schema.version', '3')");
            database.execute("INSERT INTO course(name, credit, archived, created_at) "
                    + "VALUES('数据库原理', 3, 0, '2026-03-15 10:00:00')");
            database.execute("INSERT INTO course(name, credit, archived, created_at) "
                    + "VALUES('操作系统', 3, 0, '2026-10-01 10:00:00')");
            database.execute("INSERT INTO assignment(id, course_id, title) VALUES(1, 1, '作业一')");

            SchemaMigrator.migrate(database);

            assertEquals("4", new SettingDao(database).get("schema.version", ""));
            assertEquals(1, database.queryLong(
                    "SELECT COUNT(*) FROM pragma_table_info('course') WHERE name = 'semester'"));
            assertEquals("2025-2026-2", semesterOf(database, "数据库原理"));
            assertEquals("2026-2027-1", semesterOf(database, "操作系统"));
            assertEquals(1, database.queryLong("SELECT COUNT(*) FROM assignment"));
        }
    }

    private static String semesterOf(Database database, String courseName) {
        return database.query(connection -> {
            try (var ps = connection.prepareStatement(
                    "SELECT semester FROM course WHERE name = ?")) {
                ps.setString(1, courseName);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }
}
