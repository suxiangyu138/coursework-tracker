package com.coursework.tracker.dao;

import com.coursework.tracker.model.AiConversation;
import com.coursework.tracker.model.AiMessage;
import com.coursework.tracker.util.Formatters;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** AI 对话与消息的数据访问。 */
public final class AiChatDao {

    private final Database database;

    public AiChatDao(Database database) {
        this.database = database;
    }

    public long createConversation(String title, LocalDateTime now) {
        return database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ai_conversation(title, created_at, updated_at) VALUES(?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, title);
                ps.setString(2, Formatters.db(now));
                ps.setString(3, Formatters.db(now));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            throw new SQLException("插入会话未获得主键");
        });
    }

    public List<AiConversation> listConversations() {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, title, created_at, updated_at FROM ai_conversation "
                            + "ORDER BY updated_at DESC, id DESC");
                 ResultSet rs = ps.executeQuery()) {
                List<AiConversation> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapConversation(rs));
                }
                return result;
            }
        });
    }

    public Optional<AiConversation> findConversation(long id) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, title, created_at, updated_at FROM ai_conversation WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapConversation(rs)) : Optional.empty();
                }
            }
        });
    }

    public void deleteConversation(long id) {
        database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM ai_conversation WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate();
            }
        });
    }

    public void renameConversation(long id, String title, LocalDateTime now) {
        database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE ai_conversation SET title = ?, updated_at = ? WHERE id = ?")) {
                ps.setString(1, title);
                ps.setString(2, Formatters.db(now));
                ps.setLong(3, id);
                return ps.executeUpdate();
            }
        });
    }

    public void touchConversation(long id, LocalDateTime now) {
        database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE ai_conversation SET updated_at = ? WHERE id = ?")) {
                ps.setString(1, Formatters.db(now));
                ps.setLong(2, id);
                return ps.executeUpdate();
            }
        });
    }

    public long addMessage(AiMessage message) {
        return database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ai_message(conversation_id, role, content, tool_calls, tool_call_id, created_at) "
                            + "VALUES(?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, message.conversationId());
                ps.setString(2, message.role());
                ps.setString(3, message.content());
                ps.setString(4, message.toolCallsJson());
                ps.setString(5, message.toolCallId());
                ps.setString(6, Formatters.db(message.createdAt()));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            throw new SQLException("插入消息未获得主键");
        });
    }

    /** 取最近 limit 条消息，按时间正序返回，用于构造上下文。 */
    public List<AiMessage> findRecentMessages(long conversationId, int limit) {
        String sql = "SELECT * FROM ("
                + "SELECT id, conversation_id, role, content, tool_calls, tool_call_id, created_at "
                + "FROM ai_message WHERE conversation_id = ? ORDER BY id DESC LIMIT ?"
                + ") ORDER BY id ASC";
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, conversationId);
                ps.setInt(2, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    List<AiMessage> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapMessage(rs));
                    }
                    return result;
                }
            }
        });
    }

    public List<AiMessage> findAllMessages(long conversationId) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, conversation_id, role, content, tool_calls, tool_call_id, created_at "
                            + "FROM ai_message WHERE conversation_id = ? ORDER BY id ASC")) {
                ps.setLong(1, conversationId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<AiMessage> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapMessage(rs));
                    }
                    return result;
                }
            }
        });
    }

    public void clearMessages(long conversationId) {
        database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM ai_message WHERE conversation_id = ?")) {
                ps.setLong(1, conversationId);
                return ps.executeUpdate();
            }
        });
    }

    public long countMessages(long conversationId) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM ai_message WHERE conversation_id = ?")) {
                ps.setLong(1, conversationId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    private static AiConversation mapConversation(ResultSet rs) throws SQLException {
        return new AiConversation(
                rs.getLong("id"),
                rs.getString("title"),
                SqlTypes.dateTime(rs, "created_at"),
                SqlTypes.dateTime(rs, "updated_at"));
    }

    private static AiMessage mapMessage(ResultSet rs) throws SQLException {
        return new AiMessage(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getString("role"),
                rs.getString("content"),
                rs.getString("tool_calls"),
                rs.getString("tool_call_id"),
                SqlTypes.dateTime(rs, "created_at"));
    }
}
