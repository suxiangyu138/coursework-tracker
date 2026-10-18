package com.coursework.tracker.dao;

import com.coursework.tracker.model.AiConversation;
import com.coursework.tracker.model.AiMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatDaoTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 16, 0);

    private Database database;
    private AiChatDao dao;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        SchemaMigrator.migrate(database);
        dao = new AiChatDao(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName("会话与消息的增删查")
    void conversationAndMessages() {
        long id = dao.createConversation("第一次对话", NOW);
        assertTrue(id > 0);
        assertEquals(1, dao.listConversations().size());

        dao.addMessage(AiMessage.user(id, "你好", NOW));
        dao.addMessage(AiMessage.assistant(id, "你好，有什么可以帮你？", null, NOW.plusSeconds(1)));
        dao.addMessage(AiMessage.tool(id, "call-1", "已创建作业", NOW.plusSeconds(2)));

        assertEquals(3, dao.findAllMessages(id).size());
        assertEquals(2, dao.findRecentMessages(id, 2).size());
        assertEquals("assistant", dao.findRecentMessages(id, 2).get(0).role());
        assertEquals("tool", dao.findRecentMessages(id, 2).get(1).role());

        dao.renameConversation(id, "改名", NOW.plusMinutes(1));
        AiConversation renamed = dao.findConversation(id).orElseThrow();
        assertEquals("改名", renamed.title());
        assertTrue(renamed.updatedAt().isAfter(NOW));
    }

    @Test
    @DisplayName("清空消息保留会话")
    void clearMessages() {
        long id = dao.createConversation("对话", NOW);
        dao.addMessage(AiMessage.user(id, "消息", NOW));
        dao.clearMessages(id);
        assertEquals(0, dao.countMessages(id));
        assertTrue(dao.findConversation(id).isPresent());
    }

    @Test
    @DisplayName("删除会话级联删除消息")
    void deleteConversation() {
        long id = dao.createConversation("对话", NOW);
        dao.addMessage(AiMessage.user(id, "消息", NOW));
        dao.deleteConversation(id);
        assertFalse(dao.findConversation(id).isPresent());
        assertEquals(0, database.queryLong("SELECT COUNT(*) FROM ai_message"));
    }

    @Test
    @DisplayName("最近消息按时间正序返回")
    void recentMessagesOrdered() {
        long id = dao.createConversation("对话", NOW);
        for (int i = 0; i < 5; i++) {
            dao.addMessage(AiMessage.user(id, "m" + i, NOW.plusMinutes(i)));
        }
        var messages = dao.findRecentMessages(id, 2);
        assertEquals(2, messages.size());
        assertEquals("m3", messages.get(0).content());
        assertEquals("m4", messages.get(1).content());
    }
}
