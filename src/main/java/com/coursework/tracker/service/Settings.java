package com.coursework.tracker.service;

import com.coursework.tracker.ai.AiConfig;
import com.coursework.tracker.dao.SettingDao;
import com.coursework.tracker.util.UrgencyCalculator;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** 设置项的类型化访问，所有读取都带默认值，坏数据不会让程序崩溃。 */
public final class Settings {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final SettingDao dao;

    public Settings(SettingDao dao) {
        this.dao = dao;
    }

    public boolean remindersEnabled() {
        return dao.getBoolean("reminder.enabled", true);
    }

    public void setRemindersEnabled(boolean enabled) {
        dao.setBoolean("reminder.enabled", enabled);
    }

    public boolean soundEnabled() {
        return dao.getBoolean("reminder.sound", true);
    }

    public LocalTime quietStart() {
        return parseTime(dao.get("reminder.quiet.start", "23:00"), LocalTime.of(23, 0));
    }

    public LocalTime quietEnd() {
        return parseTime(dao.get("reminder.quiet.end", "07:00"), LocalTime.of(7, 0));
    }

    public void setQuietWindow(LocalTime start, LocalTime end) {
        dao.set("reminder.quiet.start", TIME_FORMAT.format(start));
        dao.set("reminder.quiet.end", TIME_FORMAT.format(end));
    }

    public double horizonHours() {
        double value = dao.getDouble("urgency.horizonHours", UrgencyCalculator.DEFAULT_HORIZON_HOURS);
        return Math.max(1, Math.min(8760, value));
    }

    public void setHorizonHours(double hours) {
        dao.set("urgency.horizonHours", String.valueOf(hours));
    }

    public UrgencyCalculator.Weights weights() {
        String raw = dao.get("urgency.weights", "0.55,0.30,0.15");
        try {
            String[] parts = raw.split(",");
            if (parts.length == 3) {
                return UrgencyCalculator.Weights.of(
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()));
            }
        } catch (IllegalArgumentException e) {
            // 落到默认值
        }
        return UrgencyCalculator.Weights.DEFAULT;
    }

    public String theme() {
        return dao.get("theme", "light");
    }

    public void setTheme(String theme) {
        dao.set("theme", theme);
    }

    public boolean autoBackup() {
        return dao.getBoolean("backup.auto", true);
    }

    public AiConfig aiConfig() {
        return new AiConfig(
                dao.getBoolean("ai.enabled", false),
                dao.get("ai.apiKey", ""),
                dao.get("ai.baseUrl", AiConfig.DEFAULT_BASE_URL),
                dao.get("ai.model", AiConfig.DEFAULT_MODEL));
    }

    public void setAiConfig(AiConfig config) {
        dao.setBoolean("ai.enabled", config.enabled());
        dao.set("ai.apiKey", config.apiKey() == null ? "" : config.apiKey().trim());
        dao.set("ai.baseUrl", config.baseUrl() == null || config.baseUrl().isBlank()
                ? AiConfig.DEFAULT_BASE_URL : config.baseUrl().trim());
        dao.set("ai.model", config.model() == null || config.model().isBlank()
                ? AiConfig.DEFAULT_MODEL : config.model().trim());
    }

    /** 当前时刻是否落在静音时段（支持 23:00–07:00 这种跨天区间）。 */
    public boolean inQuietWindow(LocalDateTime now) {
        LocalTime start = quietStart();
        LocalTime end = quietEnd();
        if (start.equals(end)) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }

    private static LocalTime parseTime(String raw, LocalTime fallback) {
        try {
            return LocalTime.parse(raw, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
