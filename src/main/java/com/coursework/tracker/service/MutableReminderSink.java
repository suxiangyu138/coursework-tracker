package com.coursework.tracker.service;

import com.coursework.tracker.model.PlannedReminder;

import java.util.List;
import java.util.logging.Logger;

/**
 * 可替换出口：启动时服务先拿到它，界面/托盘就绪后再设置真正的实现，
 * 避免"服务创建"与"界面创建"的循环依赖。
 */
public final class MutableReminderSink implements ReminderSink {

    private static final Logger LOG = Logger.getLogger(MutableReminderSink.class.getName());

    private volatile ReminderSink delegate;

    public void setDelegate(ReminderSink delegate) {
        this.delegate = delegate;
    }

    @Override
    public void notify(List<PlannedReminder> reminders) {
        ReminderSink target = delegate;
        if (target != null) {
            target.notify(reminders);
            return;
        }
        for (PlannedReminder reminder : reminders) {
            LOG.info("[提醒] " + reminder.message());
        }
    }
}
