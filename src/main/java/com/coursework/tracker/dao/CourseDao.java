package com.coursework.tracker.dao;

import com.coursework.tracker.model.Course;
import com.coursework.tracker.util.Formatters;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 课程数据访问。 */
public final class CourseDao {

    private static final String COLUMNS =
            "id, name, code, credit, teacher, semester, target_score, archived, created_at";

    private final Database database;

    public CourseDao(Database database) {
        this.database = database;
    }

    public List<Course> findAll() {
        return findAll(false);
    }

    public List<Course> findAll(boolean includeArchived) {
        return database.query(connection -> {
            String sql = "SELECT " + COLUMNS + " FROM course "
                    + (includeArchived ? "" : "WHERE archived = 0 ")
                    + "ORDER BY archived, name";
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                List<Course> courses = new ArrayList<>();
                while (rs.next()) {
                    courses.add(map(rs));
                }
                return courses;
            }
        });
    }

    public Optional<Course> findById(long id) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM course WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    public Optional<Course> findByName(String name) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM course WHERE name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    public long insert(Course course) {
        return database.transaction(connection -> {
            String sql = "INSERT INTO course(name, code, credit, teacher, semester, target_score, archived, created_at) "
                    + "VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, course.name());
                ps.setString(2, course.code());
                ps.setDouble(3, course.credit());
                ps.setString(4, course.teacher());
                ps.setString(5, course.semester());
                if (course.targetScore() == null) {
                    ps.setObject(6, null);
                } else {
                    ps.setDouble(6, course.targetScore());
                }
                ps.setInt(7, course.archived() ? 1 : 0);
                ps.setString(8, Formatters.db(course.createdAt()));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            throw new SQLException("插入课程后未获得主键");
        });
    }

    public void update(Course course) {
        int changed = database.transaction(connection -> {
            String sql = "UPDATE course SET name = ?, code = ?, credit = ?, teacher = ?, semester = ?, "
                    + "target_score = ?, archived = ? WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, course.name());
                ps.setString(2, course.code());
                ps.setDouble(3, course.credit());
                ps.setString(4, course.teacher());
                ps.setString(5, course.semester());
                if (course.targetScore() == null) {
                    ps.setObject(6, null);
                } else {
                    ps.setDouble(6, course.targetScore());
                }
                ps.setInt(7, course.archived() ? 1 : 0);
                ps.setLong(8, course.id());
                return ps.executeUpdate();
            }
        });
        if (changed == 0) {
            throw new DataAccessException("课程不存在: " + course.id());
        }
    }

    public void delete(long id) {
        int changed = database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM course WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate();
            }
        });
        if (changed == 0) {
            throw new DataAccessException("课程不存在: " + id);
        }
    }

    public void setArchived(long id, boolean archived) {
        database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE course SET archived = ? WHERE id = ?")) {
                ps.setInt(1, archived ? 1 : 0);
                ps.setLong(2, id);
                return ps.executeUpdate();
            }
        });
    }

    public long countAssignments(long courseId) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM assignment WHERE course_id = ?")) {
                ps.setLong(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    private static Course map(ResultSet rs) throws SQLException {
        return new Course(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getDouble("credit"),
                rs.getString("teacher"),
                rs.getString("semester"),
                SqlTypes.nullableDouble(rs, "target_score"),
                rs.getInt("archived") != 0,
                SqlTypes.dateTime(rs, "created_at"));
    }
}
