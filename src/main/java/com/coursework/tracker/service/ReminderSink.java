package com.coursework.tracker.service;

import com.coursework.tracker.model.PlannedReminder;

import java.util.List;

/** 提醒的展示出口：桌面实现是托盘气泡，测试实现是内存收集器。 */
@FunctionalInterface
public interface ReminderSink {

    void notify(List<PlannedReminder> reminders);
}
