package com.coursework.tracker.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek Chat Completions 客户端（OpenAI 兼容协议）。
 *
 * <p>只实现对话与 function calling，不引入额外 SDK，便于在课程设计中讲清楚 HTTP 与 JSON 处理。
 */
public final class DeepSeekClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final Gson gson = new Gson();

    public record ToolCall(String id, String name, String arguments) {
    }

    public record ChatMessage(String role, String content, String toolCallId,
                              List<ToolCall> toolCalls) {

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content, null, null);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null);
        }

        public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", content, null, toolCalls);
        }

        public static ChatMessage tool(String toolCallId, String content) {
            return new ChatMessage("tool", content, toolCallId, null);
        }
    }

    public record ToolSpec(String name, String description, JsonObject parameters) {
    }

    public record Reply(String content, List<ToolCall> toolCalls,
                        int promptTokens, int completionTokens) {
    }

    public Reply chat(AiConfig config, List<ChatMessage> messages, List<ToolSpec> tools)
            throws AiException {
        if (!config.isConfigured()) {
            throw new AiException("AI 未启用或未配置 API Key，请先到「设置 → AI 助手」填写");
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("model", config.model());
        payload.addProperty("temperature", 0.2);

        JsonArray messageArray = new JsonArray();
        for (ChatMessage message : messages) {
            messageArray.add(toJson(message));
        }
        payload.add("messages", messageArray);

        if (tools != null && !tools.isEmpty()) {
            JsonArray toolArray = new JsonArray();
            for (ToolSpec tool : tools) {
                JsonObject function = new JsonObject();
                function.addProperty("name", tool.name());
                function.addProperty("description", tool.description());
                function.add("parameters", tool.parameters());

                JsonObject item = new JsonObject();
                item.addProperty("type", "function");
                item.add("function", function);
                toolArray.add(item);
            }
            payload.add("tools", toolArray);
            payload.addProperty("tool_choice", "auto");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.endpoint()))
                .timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new AiException(errorMessage(response.statusCode(), response.body()));
            }
            return parse(response.body());
        } catch (IOException e) {
            throw new AiException("网络请求失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("请求被中断");
        }
    }

    /** 测试连接：发一条极短的消息，验证 Key、模型名与网络。 */
    public void testConnection(AiConfig config) throws AiException {
        Reply reply = chat(config, List.of(ChatMessage.user("ping")), List.of());
        if (reply == null) {
            throw new AiException("服务没有返回内容");
        }
    }

    private JsonObject toJson(ChatMessage message) {
        JsonObject json = new JsonObject();
        json.addProperty("role", message.role());
        if (message.content() == null) {
            json.add("content", com.google.gson.JsonNull.INSTANCE);
        } else {
            json.addProperty("content", message.content());
        }
        if (message.toolCallId() != null) {
            json.addProperty("tool_call_id", message.toolCallId());
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            JsonArray calls = new JsonArray();
            for (ToolCall call : message.toolCalls()) {
                JsonObject function = new JsonObject();
                function.addProperty("name", call.name());
                function.addProperty("arguments", call.arguments());

                JsonObject item = new JsonObject();
                item.addProperty("id", call.id());
                item.addProperty("type", "function");
                item.add("function", function);
                calls.add(item);
            }
            json.add("tool_calls", calls);
        }
        return json;
    }

    private Reply parse(String body) throws AiException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error")) {
                throw new AiException("AI 服务返回错误：" + root.getAsJsonObject("error").get("message").getAsString());
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiException("AI 服务没有返回 choices");
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");

            String content = "";
            JsonElement contentElement = message.get("content");
            if (contentElement != null && !contentElement.isJsonNull()) {
                content = contentElement.getAsString();
            }

            List<ToolCall> calls = new ArrayList<>();
            JsonArray toolCalls = message.getAsJsonArray("tool_calls");
            if (toolCalls != null) {
                for (JsonElement element : toolCalls) {
                    JsonObject item = element.getAsJsonObject();
                    JsonObject function = item.getAsJsonObject("function");
                    calls.add(new ToolCall(
                            getString(item, "id"),
                            getString(function, "name"),
                            getString(function, "arguments")));
                }
            }

            int promptTokens = 0;
            int completionTokens = 0;
            JsonObject usage = root.getAsJsonObject("usage");
            if (usage != null) {
                promptTokens = getInt(usage, "prompt_tokens");
                completionTokens = getInt(usage, "completion_tokens");
            }
            return new Reply(content, List.copyOf(calls), promptTokens, completionTokens);
        } catch (AiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiException("无法解析 AI 响应：" + e.getMessage(), e);
        }
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static int getInt(JsonObject object, String key) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String errorMessage(int status, String body) {
        String detail = "";
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error")) {
                JsonObject error = root.getAsJsonObject("error");
                detail = getString(error, "message");
            }
        } catch (RuntimeException ignored) {
            // 非 JSON 错误体，保留原始片段
        }
        if (detail.isBlank()) {
            detail = body == null ? "" : body.substring(0, Math.min(300, body.length()));
        }
        String hint = switch (status) {
            case 401 -> "API Key 无效或已过期";
            case 402 -> "账户余额不足";
            case 404 -> "接口地址或模型名不正确";
            case 429 -> "请求过于频繁，请稍后再试";
            default -> "HTTP " + status;
        };
        return hint + (detail.isBlank() ? "" : "：" + detail);
    }
}
