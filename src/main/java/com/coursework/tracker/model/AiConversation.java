package com.coursework.tracker.model;

import java.time.LocalDateTime;

/** AI 对话会话。 */
public record AiConversation(long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static AiConversation draft(String title, LocalDateTime now) {
        return new AiConversation(0L, title == null || title.isBlank() ? "新对话" : title.trim(), now, now);
    }

    public AiConversation withId(long value) {
        return new AiConversation(value, title, createdAt, updatedAt);
    }

    public AiConversation withTitle(String value, LocalDateTime now) {
        return new AiConversation(id, value, createdAt, now);
    }

    @Override
    public String toString() {
        return title;
    }
}
