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

            assertEquals("2", new SettingDao(database).get("schema.version", ""));
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
}
