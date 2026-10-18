package com.coursework.tracker.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** 统一的格式化与解析入口，避免各界面各自拼格式。 */
public final class Formatters {

    public static final DateTimeFormatter DB_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter SHORT_DATE_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MM-dd");

    private Formatters() {
    }

    public static String db(LocalDateTime time) {
        return time == null ? null : DB_DATE_TIME.format(time);
    }

    public static LocalDateTime parseDb(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DB_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(text.trim(), DISPLAY_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                throw new IllegalArgumentException("无法解析时间: " + text, e);
            }
        }
    }

    /** 表格中的截止时间：10-19 23:59 周日。 */
    public static String dueText(LocalDateTime time) {
        if (time == null) {
            return "—";
        }
        return SHORT_DATE_TIME.format(time) + " " + TimeUtils.weekday(time);
    }

    public static String display(LocalDateTime time) {
        return time == null ? "—" : DISPLAY_DATE_TIME.format(time);
    }

    public static String date(LocalDate date) {
        return date == null ? "—" : DATE.format(date);
    }

    public static String shortDate(LocalDate date) {
        return date == null ? "—" : SHORT_DATE.format(date);
    }

    public static String weight(double weight) {
        if (weight <= 0) {
            return "—";
        }
        return String.format("%.0f%%", weight);
    }

    public static String score(Double score) {
        return score == null ? "—" : String.format("%.1f", score);
    }

    public static String percent(double rate) {
        return String.format("%.0f%%", rate * 100);
    }

    public static String decimal2(double value) {
        return String.format("%.2f", value);
    }

    /** 解析用户输入的时间，支持 "yyyy-MM-dd HH:mm" 与 "yyyy-MM-dd HH:mm:ss"。 */
    public static LocalDateTime parseUserDateTime(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("时间不能为空");
        }
        String value = text.trim().replace('T', ' ');
        try {
            return LocalDateTime.parse(value, DISPLAY_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value, DB_DATE_TIME);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("时间格式应为 yyyy-MM-dd HH:mm，例如 2026-10-19 23:59", ex);
            }
        }
    }
}
