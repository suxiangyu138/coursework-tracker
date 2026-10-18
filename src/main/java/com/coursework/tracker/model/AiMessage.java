package com.coursework.tracker.model;

import java.time.LocalDateTime;

/**
 * AI 对话消息。role 为 user / assistant / tool；
 * assistant 带工具调用时 toolCallsJson 保存原始 JSON，tool 消息用 toolCallId 关联。
 */
public record AiMessage(
        long id,
        long conversationId,
        String role,
        String content,
        String toolCallsJson,
        String toolCallId,
        LocalDateTime createdAt) {

    public static AiMessage user(long conversationId, String text, LocalDateTime now) {
        return new AiMessage(0L, conversationId, "user", text, null, null, now);
    }

    public static AiMessage assistant(long conversationId, String content, String toolCallsJson,
                                      LocalDateTime now) {
        return new AiMessage(0L, conversationId, "assistant", content, toolCallsJson, null, now);
    }

    public static AiMessage tool(long conversationId, String toolCallId, String content,
                                 LocalDateTime now) {
        return new AiMessage(0L, conversationId, "tool", content, null, toolCallId, now);
    }
}
