package com.coursework.tracker.model;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学期值对象：学年起始年 + 第 1/2 学期。
 *
 * <p>数据库保存规范编码（例如 {@code 2026-2027-1}），界面显示中文长标签。
 * 学期 1 = 秋季（8 月–次年 1 月），学期 2 = 春季（2 月–7 月）。
 * 这样按字符串排序也能得到正确的学期顺序。
 */
public record Semester(int startYear, int term) implements Comparable<Semester> {

    public static final String UNASSIGNED_LABEL = "未分学期";

    private static final Pattern CODE_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})-([12])$");

    /** 新学年在前；用于课程列表分组。 */
    public static final Comparator<Semester> NEWEST_FIRST =
            Comparator.comparingInt(Semester::startYear).reversed()
                    .thenComparing(Comparator.comparingInt(Semester::term).reversed());

    public Semester {
        if (startYear < 1900 || startYear > 2200) {
            throw new IllegalArgumentException("学年超出范围：" + startYear);
        }
        if (term != 1 && term != 2) {
            throw new IllegalArgumentException("学期只能是 1 或 2：" + term);
        }
    }

    /** 数据库/排序用的规范编码。 */
    public String code() {
        return String.format(Locale.ROOT, "%d-%d-%d", startYear, startYear + 1, term);
    }

    /** 界面显示：2026–2027 学年 · 第一学期（秋）。 */
    public String label() {
        return startYear + "–" + (startYear + 1) + " 学年 · "
                + (term == 1 ? "第一学期（秋）" : "第二学期（春）");
    }

    /** 列表分组用的短标签：2026–2027 秋。 */
    public String shortLabel() {
        return startYear + "–" + (startYear + 1) + " " + (term == 1 ? "秋" : "春");
    }

    /** 上一个学期。 */
    public Semester previous() {
        return term == 2 ? new Semester(startYear, 1) : new Semester(startYear - 1, 2);
    }

    /** 解析规范编码，非法值返回 empty（历史数据可能保存了自定义文本）。 */
    public static Optional<Semester> parse(String value) {
        String text = normalize(value);
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = CODE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int startYear = Integer.parseInt(matcher.group(1));
        int endYear = Integer.parseInt(matcher.group(2));
        int term = Integer.parseInt(matcher.group(3));
        if (endYear != startYear + 1) {
            return Optional.empty();
        }
        return Optional.of(new Semester(startYear, term));
    }

    /**
     * 根据日期推断学期：8 月–次年 1 月为秋季学期，2–7 月为春季学期。
     * 用于老数据升级和新建课程的默认值。
     */
    public static Semester current(LocalDateTime time) {
        int month = time.getMonthValue();
        int year = time.getYear();
        if (month >= 8) {
            return new Semester(year, 1);
        }
        if (month <= 1) {
            return new Semester(year - 1, 1);
        }
        return new Semester(year - 1, 2);
    }

    /** 去空白；空字符串统一为 null，便于数据库表示"未分学期"。 */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 任意已存值的人类可读形式：规范编码转长标签，未知文本原样显示。 */
    public static String display(String value) {
        String text = normalize(value);
        if (text == null) {
            return UNASSIGNED_LABEL;
        }
        return parse(text).map(Semester::label).orElse(text);
    }

    /** 短显示形式，用于侧栏分组标题。 */
    public static String displayShort(String value) {
        String text = normalize(value);
        if (text == null) {
            return UNASSIGNED_LABEL;
        }
        return parse(text).map(Semester::shortLabel).orElse(text);
    }

    @Override
    public int compareTo(Semester other) {
        int byYear = Integer.compare(startYear, other.startYear);
        return byYear != 0 ? byYear : Integer.compare(term, other.term);
    }
}
