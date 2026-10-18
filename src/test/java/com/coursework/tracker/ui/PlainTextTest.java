package com.coursework.tracker.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlainTextTest {

    @Test
    @DisplayName("去掉加粗与行内代码标记")
    void stripsBoldAndInlineCode() {
        assertEquals("这是加粗和代码", PlainText.render("这是**加粗**和`代码`"));
        assertEquals("下划线加粗", PlainText.render("__下划线加粗__"));
        assertEquals("删除线", PlainText.render("~~删除线~~"));
    }

    @Test
    @DisplayName("标题、引用、分隔线转纯文本")
    void stripsBlockMarkers() {
        assertEquals("标题\n· 一\n· 二", PlainText.render("### 标题\n- 一\n- 二"));
        assertEquals("引用内容", PlainText.render("> 引用内容"));
        assertEquals("上\n下", PlainText.render("上\n---\n下"));
    }

    @Test
    @DisplayName("代码块保留内容、去掉围栏")
    void stripsCodeFence() {
        String input = "示例：\n```java\nint a = 1;\n```\n结束";
        assertEquals("示例：\nint a = 1;\n结束", PlainText.render(input));
    }

    @Test
    @DisplayName("链接只保留文字")
    void stripsLinks() {
        assertEquals("参考文档", PlainText.render("参考[文档](https://example.com)"));
    }

    @Test
    @DisplayName("纯文本原样保留，空内容返回空串")
    void keepsPlainText() {
        assertEquals("这是一段普通文本。\n第二行", PlainText.render("这是一段普通文本。\n第二行"));
        assertEquals("", PlainText.render(null));
        assertEquals("", PlainText.render("   "));
    }
}
