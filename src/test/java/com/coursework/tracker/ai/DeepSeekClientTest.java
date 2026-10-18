package com.coursework.tracker.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekClientTest {

    private ServerSocket server;
    private Thread serverThread;
    private final AtomicReference<String> requestBody = new AtomicReference<>("");

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    private AiConfig config() {
        return new AiConfig(true, "test-key", "http://127.0.0.1:" + server.getLocalPort(), "test-model");
    }

    private void startServer(int status, String response) throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        serverThread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                byte[] data = new byte[65536];
                int total = 0;
                int headerEnd = -1;
                int contentLength = -1;
                InputStream in = socket.getInputStream();
                Pattern lengthPattern = Pattern.compile("(?i)Content-Length:\\s*(\\d+)");
                while (total < data.length) {
                    int read = in.read(data, total, data.length - total);
                    if (read < 0) {
                        break;
                    }
                    total += read;
                    String text = new String(data, 0, total, StandardCharsets.UTF_8);
                    if (headerEnd < 0) {
                        headerEnd = text.indexOf("\r\n\r\n");
                        if (headerEnd >= 0) {
                            Matcher matcher = lengthPattern.matcher(text.substring(0, headerEnd));
                            contentLength = matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
                        }
                    }
                    if (headerEnd >= 0 && total >= headerEnd + 4 + contentLength) {
                        break;
                    }
                }
                String text = new String(data, 0, total, StandardCharsets.UTF_8);
                requestBody.set(headerEnd >= 0 ? text.substring(headerEnd + 4) : "");

                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                String head = "HTTP/1.1 " + status + " OK\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + body.length + "\r\n"
                        + "Connection: close\r\n\r\n";
                socket.getOutputStream().write(head.getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().write(body);
                socket.getOutputStream().flush();
            } catch (IOException ignored) {
                // 测试结束时的正常关闭
            }
        });
        serverThread.start();
    }

    @Test
    @DisplayName("解析普通文本回复与 usage")
    void parseContent() throws Exception {
        startServer(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"你好，有什么可以帮你？\"}}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8}}");
        DeepSeekClient.Reply reply = new DeepSeekClient().chat(config(),
                List.of(DeepSeekClient.ChatMessage.user("你好")), List.of());
        assertEquals("你好，有什么可以帮你？", reply.content());
        assertEquals(12, reply.promptTokens());
        assertEquals(8, reply.completionTokens());
        assertTrue(requestBody.get().contains("\"model\":\"test-model\""));
    }

    @Test
    @DisplayName("解析 function calling 返回")
    void parseToolCalls() throws Exception {
        startServer(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"list_assignments\",\"arguments\":\"{}\"}}]}}]}");
        DeepSeekClient.Reply reply = new DeepSeekClient().chat(config(),
                List.of(DeepSeekClient.ChatMessage.user("这周有什么作业")), AiTools.specs());
        assertEquals(1, reply.toolCalls().size());
        assertEquals("list_assignments", reply.toolCalls().get(0).name());
        assertTrue(requestBody.get().contains("\"tools\""));
        assertTrue(requestBody.get().contains("create_assignment"));
    }

    @Test
    @DisplayName("401 映射成可读错误")
    void parseHttpError() throws Exception {
        startServer(401, "{\"error\":{\"message\":\"invalid api key\"}}");
        AiException error = assertThrows(AiException.class, () -> new DeepSeekClient().chat(config(),
                List.of(DeepSeekClient.ChatMessage.user("你好")), List.of()));
        assertTrue(error.getMessage().contains("API Key"));
        assertTrue(error.getMessage().contains("invalid api key"));
    }

    @Test
    @DisplayName("endpoint 规范化")
    void endpoint() {
        assertEquals("https://api.deepseek.com/chat/completions",
                AiConfig.disabled().endpoint());
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                new AiConfig(true, "k", "https://api.deepseek.com/v1/", "m").endpoint());
        assertEquals("https://api.deepseek.com/chat/completions",
                new AiConfig(true, "k", "https://api.deepseek.com/chat/completions", "m").endpoint());
    }

    @Test
    @DisplayName("未配置 Key 时不发请求")
    void missingKey() {
        AiException error = assertThrows(AiException.class, () -> new DeepSeekClient().chat(
                new AiConfig(true, "", "https://api.deepseek.com", "m"),
                List.of(DeepSeekClient.ChatMessage.user("hi")), List.of()));
        assertTrue(error.getMessage().contains("API Key"));
    }
}
