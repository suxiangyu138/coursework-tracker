package com.coursework.tracker.model;

/**
 * 倒计时值对象。{@code overdue} 为真时各字段表示"已过去"的量。
 * 展示规则见 {@link #display()}。
 */
public record Countdown(boolean overdue, long days, long hours, long minutes, long seconds) {

    public static Countdown zero() {
        return new Countdown(true, 0, 0, 0, 0);
    }

    public boolean justNow() {
        return overdue && days == 0 && hours == 0 && minutes == 0;
    }

    /** 人类可读倒计时：3天 04:12:05 / 04:12:05 / 逾期 2天 03:12 / 刚刚截止。 */
    public String display() {
        if (overdue) {
            if (justNow()) {
                return "刚刚截止";
            }
            if (days > 0) {
                return String.format("逾期 %d天 %02d:%02d", days, hours, minutes);
            }
            if (hours > 0) {
                return String.format("逾期 %d时%02d分", hours, minutes);
            }
            return String.format("逾期 %d分", Math.max(minutes, 1));
        }
        if (days > 0) {
            return String.format("%d天 %02d:%02d:%02d", days, hours, minutes, seconds);
        }
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
