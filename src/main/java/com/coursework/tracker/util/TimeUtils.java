package com.coursework.tracker.util;

import com.coursework.tracker.model.Countdown;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 时间计算与展示工具：倒计时、逾期判定、人类可读时长。 */
public final class TimeUtils {

    private TimeUtils() {
    }

    /**
     * 计算倒计时。截止时间等于当前时间也算逾期（与教务系统"到点即截止"一致）。
     */
    public static Countdown countdown(LocalDateTime now, LocalDateTime dueAt) {
        Duration duration = Duration.between(now, dueAt);
        boolean overdue = duration.isNegative() || duration.isZero();
        Duration abs = duration.abs();
        return new Countdown(overdue, abs.toDays(), abs.toHoursPart(),
                abs.toMinutesPart(), abs.toSecondsPart());
    }

    public static boolean isOverdue(LocalDateTime now, LocalDateTime dueAt) {
        return !dueAt.isAfter(now);
    }

    public static boolean isSameDay(LocalDateTime time, LocalDate day) {
        return day.equals(time.toLocalDate());
    }

    /** 剩余时间的粗略描述，用于提醒文案："3 天" / "5 小时" / "12 分钟"。 */
    public static String remainingText(LocalDateTime now, LocalDateTime dueAt) {
        Duration duration = Duration.between(now, dueAt);
        if (duration.isNegative() || duration.isZero()) {
            Duration past = duration.abs();
            long days = past.toDays();
            if (days > 0) {
                return "已逾期 " + days + " 天";
            }
            long hours = past.toHours();
            if (hours > 0) {
                return "已逾期 " + hours + " 小时";
            }
            return "已逾期 " + Math.max(1, past.toMinutes()) + " 分钟";
        }
        if (duration.toDays() >= 1) {
            return duration.toDays() + " 天";
        }
        if (duration.toHours() >= 1) {
            return duration.toHours() + " 小时";
        }
        return Math.max(1, duration.toMinutes()) + " 分钟";
    }

    /** 中文星期："周一" … "周日"。 */
    public static String weekday(LocalDateTime time) {
        return switch (time.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    public static DayOfWeek dayOfWeek(LocalDateTime time) {
        return time.getDayOfWeek();
    }

    /** 把分钟数格式化为"3 小时 20 分" / "45 分钟"。 */
    public static String humanMinutes(int minutes) {
        if (minutes <= 0) {
            return "0 分钟";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return rest + " 分钟";
        }
        return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分";
    }
}
