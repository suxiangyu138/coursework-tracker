package com.coursework.tracker.dao;

import com.coursework.tracker.util.Formatters;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** DAO 内部共用的结果集读取辅助方法。 */
final class SqlTypes {

    private SqlTypes() {
    }

    static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        return Formatters.parseDb(rs.getString(column));
    }
}
