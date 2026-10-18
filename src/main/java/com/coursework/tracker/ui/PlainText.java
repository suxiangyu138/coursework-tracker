package com.coursework.tracker.ui;

import java.util.regex.Pattern;

/**
 * 把模型回复中的 Markdown 标记转成纯文本。
 * 系统提示词已经要求模型输出纯文本，这里再做一层兜底，避免历史消息或模型不听话时出现 **、# 等符号。
 */
public final class PlainText {

    private static final Pattern CODE_FENCE = Pattern.compile("(?m)^[ \\t]*```[^\\n]*$");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*\\n]+)\\*\\*");
    private static final Pattern BOLD_UNDERSCORE = Pattern.compile("__([^_\\n]+)__");
    private static final Pattern STRIKE = Pattern.compile("~~([^~\\n]+)~~");
    private static final Pattern HEADING = Pattern.compile("(?m)^[ \\t]*#{1,6}[ \\t]*");
    private static final Pattern QUOTE = Pattern.compile("(?m)^[ \\t]*>[ \\t]?");
    private static final Pattern RULE = Pattern.compile("(?m)^[ \\t]*([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$");
    private static final Pattern BULLET = Pattern.compile("(?m)^[ \\t]*[-*+][ \\t]+");
    private static final Pattern LINK = Pattern.compile("!?\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\\n[ \\t]*\\n+");

    private PlainText() {
    }

    public static String render(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String result = text.replace("\r\n", "\n").replace('\r', '\n');
        result = CODE_FENCE.matcher(result).replaceAll("");
        result = INLINE_CODE.matcher(result).replaceAll("$1");
        result = BOLD.matcher(result).replaceAll("$1");
        result = BOLD_UNDERSCORE.matcher(result).replaceAll("$1");
        result = STRIKE.matcher(result).replaceAll("$1");
        result = HEADING.matcher(result).replaceAll("");
        result = QUOTE.matcher(result).replaceAll("");
        result = RULE.matcher(result).replaceAll("");
        result = BULLET.matcher(result).replaceAll("· ");
        result = LINK.matcher(result).replaceAll("$1");
        result = EXTRA_BLANK_LINES.matcher(result).replaceAll("\n");
        return result.trim();
    }
}
