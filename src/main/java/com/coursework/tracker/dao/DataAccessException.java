package com.coursework.tracker.dao;

/** 数据访问层异常，包装 SQLException 并保留原因。 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
