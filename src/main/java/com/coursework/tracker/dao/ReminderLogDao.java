package com.coursework.tracker.dao;

import com.coursework.tracker.model.FiredKey;
import com.coursework.tracker.model.ReminderRule;
import com.coursework.tracker.util.Formatters;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** 提醒记录数据访问，唯一约束用于去重兜底。 */
public final class ReminderLogDao {

    private final Database database;

    public ReminderLogDao(Database database) {
        this.database = database;
    }

    /** 批量查询已触发记录，key 为 (作业, 规则)，value 为触发时间。 */
    public Map<FiredKey, LocalDateTime> findFired(Collection<Long> assignmentIds) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(assignmentIds.size(), "?"));
        String sql = "SELECT assignment_id, rule_code, fired_at FROM reminder_log "
                + "WHERE assignment_id IN (" + placeholders + ")";
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

    /** 插入提醒记录；违反唯一约束时返回 false（表示已提醒过）。 */
    public boolean insertIgnore(long assignmentId, ReminderRule rule, LocalDateTime firedAt) {
        return database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO reminder_log(assignment_id, rule_code, fired_at) VALUES(?, ?, ?)")) {
                ps.setLong(1, assignmentId);
                ps.setString(2, rule.code());
                ps.setString(3, Formatters.db(firedAt));
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

    public long countAll() {
        return database.queryLong("SELECT COUNT(*) FROM reminder_log");
    }
}
