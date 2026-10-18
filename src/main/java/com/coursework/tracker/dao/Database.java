package com.coursework.tracker.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 单连接 SQLite 封装：开启外键、WAL、忙等待超时；所有访问串行化，
 * 桌面单机场景下简单可靠。事务模板负责提交/回滚与自动提交状态恢复。
 */
public final class Database implements AutoCloseable {

    private final Connection connection;
    private final Path file;

    private Database(Connection connection, Path file) {
        this.connection = connection;
        this.file = file;
    }

    public static Database open(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            configure(connection);
            return new Database(connection, file);
        } catch (SQLException | IOException e) {
            throw new DataAccessException("无法打开数据库: " + file, e);
        }
    }

    /** 测试用内存库。 */
    public static Database inMemory() {
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            configure(connection);
            return new Database(connection, null);
        } catch (SQLException e) {
            throw new DataAccessException("无法创建内存数据库", e);
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 3000");
            try {
                statement.execute("PRAGMA journal_mode = WAL");
            } catch (SQLException ignored) {
                // 内存库不支持 WAL，忽略
            }
            statement.execute("PRAGMA synchronous = NORMAL");
        }
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    public synchronized <T> T query(SqlWork<T> work) {
        try {
            return work.execute(connection);
        } catch (SQLException e) {
            throw new DataAccessException("数据库查询失败", e);
        }
    }

    public synchronized <T> T transaction(SqlWork<T> work) {
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                safeRollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("数据库事务失败", e);
        }
    }

    private void safeRollback() {
        try {
            connection.rollback();
        } catch (SQLException e) {
            // 回滚失败时保留原始异常
        }
    }

    /** 执行一条不返回结果的语句（测试与初始化用）。 */
    public synchronized void execute(String sql) {
        query(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            return null;
        });
    }

    /** 查询单个整数（测试用）。 */
    public synchronized long queryLong(String sql) {
        return query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 var rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    /**
     * 用 SQLite 的 {@code VACUUM INTO} 生成一致性快照，即使数据库正在被本进程使用也安全。
     * 目标文件已存在时会先删除。
     */
    public synchronized Path backupTo(Path target) {
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new DataAccessException("准备备份文件失败: " + target, e);
        }
        String escaped = target.toAbsolutePath().toString().replace("'", "''");
        execute("VACUUM INTO '" + escaped + "'");
        return target;
    }

    public Path file() {
        return file;
    }

    @Override
    public synchronized void close() {
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA optimize");
            } catch (SQLException ignored) {
                // 关闭前的优化失败不影响退出
            }
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessException("关闭数据库失败", e);
        }
    }
}
