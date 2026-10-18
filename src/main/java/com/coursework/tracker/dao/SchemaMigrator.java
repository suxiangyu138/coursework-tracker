package com.coursework.tracker.dao;

import com.coursework.tracker.model.Semester;
import com.coursework.tracker.util.Formatters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 建表与版本迁移。当前版本 v4；后续结构变更以追加迁移的方式实现，
 * 保证老用户的数据库可以平滑升级。
 */
public final class SchemaMigrator {

    private static final Logger LOG = Logger.getLogger(SchemaMigrator.class.getName());
    private static final int CURRENT_VERSION = 4;

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
                // 兼容"有表但没有版本号"的异常库：schema.sql 的 CREATE TABLE IF NOT EXISTS
                // 不会给已存在的表补列，这里兜底检查一次。
                ensureSemesterColumn(connection);
                LOG.info("已创建数据库结构 v" + version);
            } else {
                if (version < 2) {
                    migrateToV2(connection);
                    version = 2;
                    LOG.info("数据库已升级到 v2（提醒记录改为按天去重）");
                }
                if (version < 3) {
                    migrateToV3(connection);
                    version = 3;
                    LOG.info("数据库已升级到 v3（新增 AI 对话表）");
                }
                if (version < 4) {
                    migrateToV4(connection);
                    version = 4;
                }
            }
            seedDefaults(connection);
            writeVersion(connection, Math.max(version, CURRENT_VERSION));
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

    /** v3：AI 对话与消息表。 */
    private static void migrateToV3(Connection connection) throws SQLException {
        String[] statements = {
                "CREATE TABLE IF NOT EXISTS ai_conversation ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "title TEXT NOT NULL,"
                        + "created_at TEXT NOT NULL,"
                        + "updated_at TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS ai_message ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "conversation_id INTEGER NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,"
                        + "role TEXT NOT NULL CHECK (role IN ('user', 'assistant', 'tool')),"
                        + "content TEXT,"
                        + "tool_calls TEXT,"
                        + "tool_call_id TEXT,"
                        + "created_at TEXT NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_ai_message_conv ON ai_message(conversation_id, id)"
        };
        for (String statement : statements) {
            try (Statement st = connection.createStatement()) {
                st.execute(statement);
            }
        }
    }

    /**
     * v4：课程增加学期。只加列、不改动原有字段；老课程按创建时间推断学期
     * （8 月–次年 1 月为秋季学期，2–7 月为春季学期），随后可在界面里修改。
     * 这是纯增量迁移，不会删除任何课程、作业或提醒记录。
     */
    private static void migrateToV4(Connection connection) throws SQLException {
        if (!tableExists(connection, "course")) {
            LOG.warning("数据库中没有 course 表，跳过 v4 学期迁移");
            return;
        }
        ensureSemesterColumn(connection);
        int inferred = inferSemesters(connection);
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_course_semester ON course(semester)");
        }
        LOG.info("数据库已升级到 v4（课程支持学期，自动推断 " + inferred + " 门课程）");
    }

    /** 幂等地补 semester 列（老库升级或异常库兜底）。 */
    private static void ensureSemesterColumn(Connection connection) throws SQLException {
        if (!tableExists(connection, "course") || hasColumn(connection, "course", "semester")) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE course ADD COLUMN semester TEXT");
        }
        LOG.info("course 表已增加 semester 列");
    }

    /**
     * 老课程没有学期信息，用创建时间推断一个合理默认值。
     * 只处理 semester 为空的行，已有值（包括用户手动设置）绝不覆盖。
     */
    private static int inferSemesters(Connection connection) throws SQLException {
        List<CourseCreated> pending = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, created_at FROM course WHERE semester IS NULL OR trim(semester) = ''");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    LocalDateTime createdAt = Formatters.parseDb(rs.getString("created_at"));
                    if (createdAt != null) {
                        pending.add(new CourseCreated(rs.getLong("id"), createdAt));
                    }
                } catch (IllegalArgumentException ignored) {
                    // 时间格式异常时保持"未分学期"，不阻断升级
                }
            }
        }
        if (pending.isEmpty()) {
            return 0;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE course SET semester = ? WHERE id = ?")) {
            for (CourseCreated course : pending) {
                ps.setString(1, Semester.current(course.createdAt()).code());
                ps.setLong(2, course.id());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return pending.size();
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column)
            throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private record CourseCreated(long id, LocalDateTime createdAt) {
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
