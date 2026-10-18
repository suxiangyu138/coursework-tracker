package com.coursework.tracker.core;

import com.coursework.tracker.model.PlannedReminder;

import java.util.List;

/** 应用内事件定义。 */
public final class AppEvents {

    private AppEvents() {
    }

    /** 作业发生增删改，界面需要刷新表格与统计。 */
    public record AssignmentChanged() {
    }

    /** 课程发生增删改。 */
    public record CourseChanged() {
    }

    /** 提醒已触发，托盘/提示层需要展示。 */
    public record RemindersFired(List<PlannedReminder> reminders) {
        public RemindersFired {
            reminders = List.copyOf(reminders);
        }
    }
}
