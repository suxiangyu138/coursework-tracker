package com.coursework.tracker.model;

import java.time.Duration;

/**
 * 提醒档位。{@code offset} 为距截止时间提前量；逾期汇总规则没有偏移量。
 */
public enum ReminderRule {

    T24H("T24H", Duration.ofHours(24), "提前 24 小时"),
    T6H("T6H", Duration.ofHours(6), "提前 6 小时"),
    T1H("T1H", Duration.ofHours(1), "提前 1 小时"),
    T15M("T15M", Duration.ofMinutes(15), "提前 15 分钟"),
    OVERDUE("OVERDUE", null, "已逾期");

    private final String code;
    private final Duration offset;
    private final String label;

    ReminderRule(String code, Duration offset, String label) {
        this.code = code;
        this.offset = offset;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public Duration offset() {
        return offset;
    }

    public String label() {
        return label;
    }

    public boolean isTimed() {
        return offset != null;
    }

    public static ReminderRule fromCode(String code) {
        for (ReminderRule rule : values()) {
            if (rule.code.equals(code)) {
                return rule;
            }
        }
        return null;
    }

    /** 按提前量从大到小排序后的定时规则（不含逾期汇总）。 */
    public static ReminderRule[] timedRules() {
        return new ReminderRule[]{T24H, T6H, T1H, T15M};
    }
}
