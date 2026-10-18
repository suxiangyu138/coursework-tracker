package com.coursework.tracker.util;

import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;

/**
 * 课程名归一化与相似度。用于避免"同一门课因为空格、全半角、大小写或一两个字的差别"
 * 被重复创建。
 *
 * <p>归一化只做安全的等价变换：Unicode NFKC（全角→半角）、去掉所有空白、
 * 去掉不影响课程含义的连接符（- _ · • — – : 等）、统一小写。
 * 括号内容（如"（实践）""（下）"）会保留，因为它通常代表不同的课程。
 */
public final class CourseNameMatcher {

    private static final Set<Character> IGNORED_SEPARATORS = Set.of(
            '-', '_', '·', '•', '—', '–', ':', '：', '/', '\\', '|', '｜');

    private CourseNameMatcher() {
    }

    /** 归一化课程名；null/纯空白返回空字符串。 */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String text = Normalizer.normalize(name, Normalizer.Form.NFKC);
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '\u3000') {
                continue;
            }
            if (IGNORED_SEPARATORS.contains(c)) {
                continue;
            }
            builder.append(Character.toLowerCase(c));
        }
        return builder.toString().trim().toLowerCase(Locale.ROOT);
    }

    /** 归一化后完全相同。 */
    public static boolean same(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        return !a.isEmpty() && a.equals(b);
    }

    /**
     * 0–1 的相似度：编辑距离占比与包含关系取较大值。
     * 只用于"提示可能重复"，不会自动把两门课合并。
     */
    public static double similarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 1;
        }
        int maxLength = Math.max(a.length(), b.length());
        double editRatio = 1.0 - (double) levenshtein(a, b) / maxLength;
        double containment = 0;
        if (a.contains(b) || b.contains(a)) {
            containment = (double) Math.min(a.length(), b.length()) / maxLength;
        }
        return Math.max(editRatio, containment);
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            char leftChar = left.charAt(i - 1);
            for (int j = 1; j <= right.length(); j++) {
                int cost = leftChar == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
