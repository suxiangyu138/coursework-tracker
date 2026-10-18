package com.coursework.tracker.dao;

import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.util.Formatters;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 作业数据访问。 */
public final class AssignmentDao {

    private static final String COLUMNS = "id, course_id, title, due_at, weight, estimated_minutes, "
            + "status, submitted_at, score, notes, created_at, updated_at";

    private final Database database;

    public AssignmentDao(Database database) {
        this.database = database;
    }

    public List<Assignment> findAll() {
        return queryList("SELECT " + COLUMNS + " FROM assignment ORDER BY due_at");
    }

    public List<Assignment> findByCourse(long courseId) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM assignment WHERE course_id = ? ORDER BY due_at")) {
                ps.setLong(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Assignment> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(map(rs));
                    }
                    return result;
                }
            }
        });
    }

    /** 未完成且截止时间不晚于 horizon 的作业（提醒扫描候选）。 */
    public List<Assignment> findActiveUntil(LocalDateTime horizon) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM assignment "
                            + "WHERE status IN ('TODO', 'DOING') AND due_at <= ? ORDER BY due_at")) {
                ps.setString(1, Formatters.db(horizon));
                try (ResultSet rs = ps.executeQuery()) {
                    List<Assignment> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(map(rs));
                    }
                    return result;
                }
            }
        });
    }

    public Optional<Assignment> findById(long id) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM assignment WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    public long insert(Assignment assignment) {
        return database.transaction(connection -> {
            String sql = "INSERT INTO assignment(course_id, title, due_at, weight, estimated_minutes, "
                    + "status, submitted_at, score, notes, created_at, updated_at) "
                    + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, assignment, false);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            throw new SQLException("插入作业后未获得主键");
        });
    }

    public void update(Assignment assignment) {
        int changed = database.transaction(connection -> {
            String sql = "UPDATE assignment SET course_id = ?, title = ?, due_at = ?, weight = ?, "
                    + "estimated_minutes = ?, status = ?, submitted_at = ?, score = ?, notes = ?, "
                    + "updated_at = ? WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, assignment.courseId());
                ps.setString(2, assignment.title());
                ps.setString(3, Formatters.db(assignment.dueAt()));
                ps.setDouble(4, assignment.weight());
                setNullableInt(ps, 5, assignment.estimatedMinutes());
                ps.setString(6, assignment.status().name());
                ps.setString(7, Formatters.db(assignment.submittedAt()));
                setNullableDouble(ps, 8, assignment.score());
                ps.setString(9, assignment.notes());
                ps.setString(10, Formatters.db(assignment.updatedAt()));
                ps.setLong(11, assignment.id());
                return ps.executeUpdate();
            }
        });
        if (changed == 0) {
            throw new DataAccessException("作业不存在: " + assignment.id());
        }
    }

    public void delete(long id) {
        int changed = database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM assignment WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate();
            }
        });
        if (changed == 0) {
            throw new DataAccessException("作业不存在: " + id);
        }
    }

    public long countByStatus(AssignmentStatus status) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM assignment WHERE status = ?")) {
                ps.setString(1, status.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    private List<Assignment> queryList(String sql) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                List<Assignment> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        });
    }

    private static void bind(PreparedStatement ps, Assignment assignment, boolean includeId)
            throws SQLException {
        int index = 1;
        ps.setLong(index++, assignment.courseId());
        ps.setString(index++, assignment.title());
        ps.setString(index++, Formatters.db(assignment.dueAt()));
        ps.setDouble(index++, assignment.weight());
        setNullableInt(ps, index++, assignment.estimatedMinutes());
        ps.setString(index++, assignment.status().name());
        ps.setString(index++, Formatters.db(assignment.submittedAt()));
        setNullableDouble(ps, index++, assignment.score());
        ps.setString(index++, assignment.notes());
        ps.setString(index++, Formatters.db(assignment.createdAt()));
        ps.setString(index++, Formatters.db(assignment.updatedAt()));
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static Assignment map(ResultSet rs) throws SQLException {
        return new Assignment(
                rs.getLong("id"),
                rs.getLong("course_id"),
                rs.getString("title"),
                SqlTypes.dateTime(rs, "due_at"),
                rs.getDouble("weight"),
                SqlTypes.nullableInt(rs, "estimated_minutes"),
                AssignmentStatus.fromDb(rs.getString("status")),
                SqlTypes.dateTime(rs, "submitted_at"),
                SqlTypes.nullableDouble(rs, "score"),
                rs.getString("notes"),
                SqlTypes.dateTime(rs, "created_at"),
                SqlTypes.dateTime(rs, "updated_at"));
    }
}
