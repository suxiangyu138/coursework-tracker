package com.coursework.tracker.model;

import java.time.LocalDate;

/** 按天聚合的工作量（图表数据源）。 */
public record DayBucket(LocalDate day, int count, double weight) {

    public String label() {
        return day.getMonthValue() + "/" + day.getDayOfMonth();
    }
}
