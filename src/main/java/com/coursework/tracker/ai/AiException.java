package com.coursework.tracker.ai;

/** AI 调用或工具执行失败。 */
public class AiException extends Exception {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
