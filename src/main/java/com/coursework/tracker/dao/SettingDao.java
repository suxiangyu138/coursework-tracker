package com.coursework.tracker.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/** 键值设置表。 */
public final class SettingDao {

    private final Database database;

    public SettingDao(Database database) {
        this.database = database;
    }

    public String get(String key, String defaultValue) {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT value FROM setting WHERE key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : defaultValue;
                }
            }
        });
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void set(String key, String value) {
        database.transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO setting(key, value) VALUES(?, ?) "
                            + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                ps.setString(1, key);
                ps.setString(2, value);
                return ps.executeUpdate();
            }
        });
    }

    public void setBoolean(String key, boolean value) {
        set(key, String.valueOf(value));
    }

    public void setInt(String key, int value) {
        set(key, String.valueOf(value));
    }

    public Map<String, String> all() {
        return database.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT key, value FROM setting");
                 ResultSet rs = ps.executeQuery()) {
                Map<String, String> values = new HashMap<>();
                while (rs.next()) {
                    values.put(rs.getString("key"), rs.getString("value"));
                }
                return values;
            }
        });
    }
}
