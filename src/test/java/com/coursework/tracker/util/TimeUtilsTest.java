package com.coursework.tracker.util;

import com.coursework.tracker.model.Countdown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeUtilsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 14, 25, 49);

    @Test
    @DisplayName("剩余超过一天：显示 天+时分秒")
    void countdownMoreThanOneDay() {
        Countdown countdown = TimeUtils.countdown(NOW,
                NOW.plusDays(3).plusHours(4).plusMinutes(12).plusSeconds(5));
        assertEquals("3天 04:12:05", countdown.display());
    }

    @Test
    @DisplayName("剩余不足一天：时分秒补零")
    void countdownLessThanOneDay() {
        Countdown countdown = TimeUtils.countdown(NOW,
                NOW.plusHours(4).plusMinutes(12).plusSeconds(5));
        assertEquals("04:12:05", countdown.display());
    }

    @Test
    @DisplayName("恰好到期视为逾期，显示刚刚截止")
    void countdownExactlyDue() {
        Countdown countdown = TimeUtils.countdown(NOW, NOW);
        assertTrue(countdown.overdue());
        assertEquals("刚刚截止", countdown.display());
    }

    @Test
    @DisplayName("逾期两天余三小时：逾期 2天 03:12")
    void countdownOverdueDays() {
        Countdown countdown = TimeUtils.countdown(NOW,
                NOW.minusDays(2).minusHours(3).minusMinutes(12));
        assertEquals("逾期 2天 03:12", countdown.display());
    }

    @Test
    @DisplayName("逾期不足一小时：逾期 X分")
    void countdownOverdueMinutes() {
        Countdown countdown = TimeUtils.countdown(NOW, NOW.minusMinutes(12));
        assertEquals("逾期 12分", countdown.display());
    }

    @Test
    @DisplayName("逾期不足一分钟：刚刚截止")
    void countdownOverdueSeconds() {
        Countdown countdown = TimeUtils.countdown(NOW, NOW.minusSeconds(30));
        assertEquals("刚刚截止", countdown.display());
    }

    @Test
    @DisplayName("跨年倒计时正常")
    void countdownAcrossYear() {
        LocalDateTime now = LocalDateTime.of(2026, 12, 31, 23, 0);
        Countdown countdown = TimeUtils.countdown(now, LocalDateTime.of(2027, 1, 1, 0, 0));
        assertEquals("01:00:00", countdown.display());
    }

    @Test
    @DisplayName("闰日 2 月 29 日正常计算")
    void countdownLeapDay() {
        LocalDateTime now = LocalDateTime.of(2028, 2, 28, 23, 59);
        Countdown countdown = TimeUtils.countdown(now, LocalDateTime.of(2028, 2, 29, 23, 59));
        assertEquals("1天 00:00:00", countdown.display());
    }

    @Test
    @DisplayName("剩余时间粗描述")
    void remainingText() {
        assertEquals("1 天", TimeUtils.remainingText(NOW, NOW.plusHours(30)));
        assertEquals("5 小时", TimeUtils.remainingText(NOW, NOW.plusHours(5)));
        assertEquals("20 分钟", TimeUtils.remainingText(NOW, NOW.plusMinutes(20)));
        assertEquals("已逾期 2 天", TimeUtils.remainingText(NOW, NOW.minusDays(2)));
    }

    @Test
    @DisplayName("中文星期")
    void weekday() {
        assertEquals("周日", TimeUtils.weekday(LocalDateTime.of(2026, 10, 18, 9, 0)));
        assertEquals("周一", TimeUtils.weekday(LocalDateTime.of(2026, 10, 19, 9, 0)));
    }
}
