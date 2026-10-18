package com.coursework.tracker.core;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统一时间源。业务代码不允许直接调用 {@code LocalDateTime.now()}，
 * 一律通过本类获取，便于单元测试用 {@link Clock#fixed} 模拟任意时刻。
 */
public final class ClockProvider {

    private static volatile Clock clock = Clock.systemDefaultZone();

    private ClockProvider() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    public static Clock clock() {
        return clock;
    }

    /** 注入自定义时钟（测试用）。传 null 恢复系统时钟。 */
    public static void setClock(Clock newClock) {
        clock = (newClock == null) ? Clock.systemDefaultZone() : newClock;
    }

    public static void reset() {
        setClock(null);
    }
}
