package com.coursework.tracker.dao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 建表与版本迁移。当前版本 v1；后续结构变更以追加迁移的方式实现，
 * 保证老用户的数据库可以平滑升级。
 */
public final class SchemaMigrator {

    private static final Logger LOG = Logger.getLogger(SchemaMigrator.class.getName());
    private static final int CURRENT_VERSION = 2;

    private SchemaMigrator() {
    }

    public static void migrate(Database database) {
        database.transaction(connection -> {
            createSettingTable(connection);
            int version = readVersion(connection);
            if (version < 1) {
                for (String statement : loadSchemaStatements()) {
                    try (Statement st = connection.createStatement()) {
                        st.execute(statement);
                    }
                }
                version = CURRENT_VERSION;
                LOG.info("已创建数据库结构 v" + version);
            } else if (version < 2) {
                migrateToV2(connection);
                version = 2;
                LOG.info("数据库已升级到 v2（提醒记录改为按天去重）");
            }
            seedDefaults(connection);
            writeVersion(connection, CURRENT_VERSION);
            return null;
        });
    }

    /**
     * v2：提醒记录增加 fired_date，唯一约束改为 (作业, 规则, 日期)，
     * 使"逾期每日提醒"真正成立，同时保持普通档位只提醒一次。
     */
    private static void migrateToV2(Connection connection) throws SQLException {
        String[] statements = {
                "CREATE TABLE reminder_log_v2 ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "assignment_id INTEGER NOT NULL REFERENCES assignment(id) ON DELETE CASCADE,"
                        + "rule_code TEXT NOT NULL,"
                        + "fired_date TEXT NOT NULL,"
                        + "fired_at TEXT NOT NULL,"
                        + "UNIQUE (assignment_id, rule_code, fired_date))",
                "INSERT INTO reminder_log_v2(id, assignment_id, rule_code, fired_date, fired_at) "
                        + "SELECT id, assignment_id, rule_code, substr(fired_at, 1, 10), fired_at "
                        + "FROM reminder_log",
                "DROP TABLE reminder_log",
                "ALTER TABLE reminder_log_v2 RENAME TO reminder_log",
                "CREATE INDEX IF NOT EXISTS idx_reminder_fired ON reminder_log(fired_at)"
        };
        for (String statement : statements) {
            try (Statement st = connection.createStatement()) {
                st.execute(statement);
            }
        }
    }

    private static void createSettingTable(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS setting (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
    }

    private static int readVersion(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM setting WHERE key = 'schema.version'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void writeVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO setting(key, value) VALUES('schema.version', ?) " +
                        "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, String.valueOf(version));
            ps.executeUpdate();
        }
    }

    private static void seedDefaults(Connection connection) throws SQLException {
        String[][] defaults = {
                {"theme", "light"},
                {"reminder.enabled", "true"},
                {"reminder.quiet.start", "23:00"},
                {"reminder.quiet.end", "07:00"},
                {"reminder.sound", "true"},
                {"urgency.horizonHours", "168"},
                {"urgency.weights", "0.55,0.30,0.15"},
                {"sort.default", "URGENCY"},
                {"backup.auto", "true"},
                {"window.bounds", ""}
        };
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO setting(key, value) VALUES(?, ?)")) {
            for (String[] entry : defaults) {
                ps.setString(1, entry[0]);
                ps.setString(2, entry[1]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static List<String> loadSchemaStatements() {
        try (InputStream in = SchemaMigrator.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("缺少资源 /db/schema.sql");
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return splitStatements(sql);
        } catch (IOException e) {
            throw new IllegalStateException("读取 schema.sql 失败", e);
        }
    }

    /** 去掉注释行后按分号拆分（本项目 DDL 中不含字符串分号）。 */
    static List<String> splitStatements(String sql) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            int comment = line.indexOf("--");
            cleaned.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements;
    }
}
