package com.coursework.tracker.dao;

import com.coursework.tracker.model.FiredKey;
import com.coursework.tracker.model.ReminderHistoryItem;
import com.coursework.tracker.model.ReminderRule;
import com.coursework.tracker.util.Formatters;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 提醒记录数据访问；唯一约束为 (作业, 规则, 日期)，逾期提醒可按天重复。 */
public final class ReminderLogDao {

    private final Database database;

    public ReminderLogDao(Database database) {
        this.database = database;
    }

    /**
     * 批量查询每个 (作业, 规则) 最近一次触发时间。
     * 逾期提醒每天一条，这里取最新日期供规划器判断"今天是否已提醒"。
     */
    public Map<FiredKey, LocalDateTime> findFired(Collection<Long> assignmentIds) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(assignmentIds.size(), "?"));
        String sql = "SELECT assignment_id, rule_code, MAX(fired_at) AS fired_at FROM reminder_log "
                + "WHERE assignment_id IN (" + placeholders + ") "
                + "GROUP BY assignment_id, rule_code";
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int index = 1;
                for (Long id : assignmentIds) {
                    ps.setLong(index++, id);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    Map<FiredKey, LocalDateTime> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(new FiredKey(rs.getLong("assignment_id"), rs.getString("rule_code")),
                                SqlTypes.dateTime(rs, "fired_at"));
                    }
                    return result;
                }
            }
        });
    }

    /** 插入提醒记录；同一天同一规则重复时返回 false。 */
    public boolean insertIgnore(long assignmentId, ReminderRule rule, LocalDateTime firedAt) {
        return database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO reminder_log(assignment_id, rule_code, fired_date, fired_at) "
                            + "VALUES(?, ?, ?, ?)")) {
                ps.setLong(1, assignmentId);
                ps.setString(2, rule.code());
                ps.setString(3, firedAt.toLocalDate().toString());
                ps.setString(4, Formatters.db(firedAt));
                return ps.executeUpdate() > 0;
            }
        });
    }

    public void deleteByAssignment(long assignmentId) {
        database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM reminder_log WHERE assignment_id = ?")) {
                ps.setLong(1, assignmentId);
                return ps.executeUpdate();
            }
        });
    }

    /** 最近若干条提醒历史，用于「提醒中心」。 */
    public List<ReminderHistoryItem> findRecent(int limit) {
        String sql = "SELECT r.id, r.fired_at, r.rule_code, a.title, a.due_at, c.name AS course_name "
                + "FROM reminder_log r "
                + "JOIN assignment a ON a.id = r.assignment_id "
                + "LEFT JOIN course c ON c.id = a.course_id "
                + "ORDER BY r.fired_at DESC, r.id DESC LIMIT ?";
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    List<ReminderHistoryItem> items = new ArrayList<>();
                    while (rs.next()) {
                        ReminderRule rule = ReminderRule.fromCode(rs.getString("rule_code"));
                        items.add(new ReminderHistoryItem(
                                rs.getLong("id"),
                                SqlTypes.dateTime(rs, "fired_at"),
                                rs.getString("course_name"),
                                rs.getString("title"),
                                rule == null ? rs.getString("rule_code") : rule.label(),
                                SqlTypes.dateTime(rs, "due_at")));
                    }
                    return items;
                }
            }
        });
    }

    public long countAll() {
        return database.queryLong("SELECT COUNT(*) FROM reminder_log");
    }
}
