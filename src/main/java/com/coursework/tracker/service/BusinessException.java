package com.coursework.tracker.service;

/** 可展示给用户的业务异常（校验失败、重复数据等）。 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
