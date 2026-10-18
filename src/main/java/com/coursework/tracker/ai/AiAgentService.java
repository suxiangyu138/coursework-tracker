package com.coursework.tracker.ai;

import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.dao.AiChatDao;
import com.coursework.tracker.model.AiMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 编排：加载上下文 → 调用模型 → 解析工具调用 → 用户确认 → 执行 → 回填结果 → 继续对话。
 */
public final class AiAgentService {

    private static final int MAX_ROUNDS = 4;
    private static final int CONTEXT_MESSAGES = 40;

    private final DeepSeekClient client;
    private final AiActionExecutor executor;
    private final AiChatDao chatDao;
    private final Gson gson = new Gson();

    public AiAgentService(DeepSeekClient client, AiActionExecutor executor, AiChatDao chatDao) {
        this.client = client;
        this.executor = executor;
        this.chatDao = chatDao;
    }

    /**
     * 处理一轮用户消息（调用方需先把用户消息写入数据库）。
     *
     * @param systemPrompt 系统提示词，包含当前时间与数据概况
     * @param confirmer    写操作确认回调
     * @return 最终展示给用户的文字
     */
    public String chat(AiConfig config, long conversationId, String systemPrompt,
                       ConfirmationHandler confirmer) throws AiException {
        List<AiMessage> history = chatDao.findRecentMessages(conversationId, CONTEXT_MESSAGES);
        List<DeepSeekClient.ChatMessage> messages = new ArrayList<>();
        messages.add(DeepSeekClient.ChatMessage.system(systemPrompt));
        for (AiMessage message : history) {
            messages.add(toChatMessage(message));
        }

        for (int round = 0; round < MAX_ROUNDS; round++) {
            DeepSeekClient.Reply reply = client.chat(config, messages, AiTools.specs());

            if (reply.toolCalls().isEmpty()) {
                String content = (reply.content() == null || reply.content().isBlank())
                        ? "（模型没有返回文字内容）" : reply.content().trim();
                LocalDateTime now = ClockProvider.now();
                chatDao.addMessage(AiMessage.assistant(conversationId, content, null, now));
                chatDao.touchConversation(conversationId, now);
                return content;
            }

            LocalDateTime now = ClockProvider.now();
            String callsJson = gson.toJson(reply.toolCalls());
            chatDao.addMessage(AiMessage.assistant(conversationId, reply.content(), callsJson, now));
            messages.add(DeepSeekClient.ChatMessage.assistant(reply.content(), reply.toolCalls()));

            List<DeepSeekClient.ToolCall> mutating = reply.toolCalls().stream()
                    .filter(call -> executor.isMutating(call.name()))
                    .toList();
            boolean approved = mutating.isEmpty()
                    || confirmer.confirm(mutating.stream().map(executor::preview).toList());

            for (DeepSeekClient.ToolCall call : reply.toolCalls()) {
                String result;
                if (executor.isMutating(call.name()) && !approved) {
                    result = "用户拒绝了该操作，未执行。";
                } else {
                    try {
                        result = executor.execute(call);
                    } catch (AiException e) {
                        result = "执行失败：" + e.getMessage();
                    }
                }
                messages.add(DeepSeekClient.ChatMessage.tool(call.id(), result));
                chatDao.addMessage(AiMessage.tool(conversationId, call.id(), result, ClockProvider.now()));
            }

            if (!approved) {
                messages.add(DeepSeekClient.ChatMessage.system(
                        "用户已拒绝上述写操作。不要再次尝试执行相同修改，直接用文字说明或询问用户。"));
            }
        }
        throw new AiException("操作步骤过多，已停止。请换一种更直接的说法重试。");
    }

    private DeepSeekClient.ChatMessage toChatMessage(AiMessage message) throws AiException {
        String content = message.content() == null ? "" : message.content();
        return switch (message.role()) {
            case "user" -> DeepSeekClient.ChatMessage.user(content);
            case "tool" -> DeepSeekClient.ChatMessage.tool(message.toolCallId(), content);
            case "assistant" -> DeepSeekClient.ChatMessage.assistant(content,
                    parseToolCalls(message.toolCallsJson()));
            default -> DeepSeekClient.ChatMessage.user(content);
        };
    }

    private List<DeepSeekClient.ToolCall> parseToolCalls(String json) throws AiException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<DeepSeekClient.ToolCall> calls = gson.fromJson(json,
                    new TypeToken<List<DeepSeekClient.ToolCall>>() {
                    }.getType());
            return calls == null ? List.of() : calls;
        } catch (RuntimeException e) {
            throw new AiException("历史工具调用记录损坏：" + e.getMessage(), e);
        }
    }
}
