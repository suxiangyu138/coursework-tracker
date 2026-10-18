package com.coursework.tracker.service;

import com.coursework.tracker.core.AppEvents;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.core.EventBus;
import com.coursework.tracker.dao.AssignmentDao;
import com.coursework.tracker.dao.CourseDao;
import com.coursework.tracker.dao.ReminderLogDao;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.FiredKey;
import com.coursework.tracker.model.PlannedReminder;
import com.coursework.tracker.model.ReminderPlan;
import com.coursework.tracker.model.ReminderRule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 提醒调度：每 30 秒扫描一次，命中规则后弹托盘气泡并落库去重。
 * 静音时段的提醒只入内存队列，恢复后合并播报，避免"落库了但没看到"导致永久丢失。
 */
public final class ReminderService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ReminderService.class.getName());
    private static final long INITIAL_DELAY_SECONDS = 5;
    private static final long SCAN_PERIOD_SECONDS = 30;

    private final AssignmentDao assignmentDao;
    private final CourseDao courseDao;
    private final ReminderLogDao reminderLogDao;
    private final Settings settings;
    private final ReminderSink sink;
    private final EventBus eventBus;
    private final ReminderPlanner planner = new ReminderPlanner();
    private final ScheduledExecutorService scheduler;

    private final List<PlannedReminder> deferred = new ArrayList<>();
    private final Set<FiredKey> deferredKeys = new HashSet<>();

    private volatile LocalDateTime mutedUntil;

    public ReminderService(AssignmentDao assignmentDao, CourseDao courseDao,
                           ReminderLogDao reminderLogDao, Settings settings,
                           ReminderSink sink, EventBus eventBus) {
        this.assignmentDao = assignmentDao;
        this.courseDao = courseDao;
        this.reminderLogDao = reminderLogDao;
        this.settings = settings;
        this.sink = sink;
        this.eventBus = eventBus;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reminder-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::tickQuietly,
                INITIAL_DELAY_SECONDS, SCAN_PERIOD_SECONDS, TimeUnit.SECONDS);
        LOG.info("提醒调度已启动，扫描周期 " + SCAN_PERIOD_SECONDS + " 秒");
    }

    private void tickQuietly() {
        try {
            tick(ClockProvider.now());
        } catch (Throwable e) {
            // 任何异常都不能让调度线程死掉
            LOG.log(Level.WARNING, "提醒扫描失败", e);
        }
    }

    /** 扫描一次，返回本次实际播报的提醒（测试与"立即检查"按钮复用）。 */
    public synchronized List<PlannedReminder> tick(LocalDateTime now) {
        LocalDateTime effectiveNow = (now == null) ? ClockProvider.now() : now;
        List<Assignment> candidates = assignmentDao.findActiveUntil(effectiveNow.plusDays(7));
        if (candidates.isEmpty() && deferred.isEmpty()) {
            return List.of();
        }

        Map<FiredKey, LocalDateTime> fired = reminderLogDao.findFired(
                candidates.stream().map(Assignment::id).toList());
        Map<Long, String> courseNames = courseNames();
        boolean enabled = settings.remindersEnabled();
        boolean quiet = enabled && isQuiet(effectiveNow);
        List<ReminderRule> rules = enabled ? List.of(ReminderRule.timedRules()) : List.of();

        ReminderPlan plan = planner.plan(effectiveNow, candidates, fired, rules, courseNames, quiet);

        List<PlannedReminder> batch = new ArrayList<>();
        Set<FiredKey> batchKeys = new HashSet<>();
        if (!quiet) {
            for (PlannedReminder reminder : deferred) {
                if (batchKeys.add(reminder.key())) {
                    batch.add(reminder);
                }
            }
            deferred.clear();
            deferredKeys.clear();
        }
        for (PlannedReminder reminder : plan.toFire()) {
            if (batchKeys.add(reminder.key())) {
                batch.add(reminder);
            }
        }
        if (quiet) {
            for (PlannedReminder reminder : plan.deferred()) {
                if (deferredKeys.add(reminder.key())) {
                    deferred.add(reminder);
                }
            }
        }
        if (batch.isEmpty()) {
            return List.of();
        }

        persist(batch, effectiveNow);
        notifySafely(batch);
        eventBus.publish(new AppEvents.RemindersFired(batch));
        LOG.info("本次提醒 " + batch.size() + " 条");
        return List.copyOf(batch);
    }

    private void persist(List<PlannedReminder> batch, LocalDateTime now) {
        for (PlannedReminder reminder : batch) {
            try {
                reminderLogDao.insertIgnore(reminder.assignment().id(), reminder.rule(), now);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "写入提醒记录失败: " + reminder.message(), e);
            }
        }
    }

    private void notifySafely(List<PlannedReminder> batch) {
        try {
            sink.notify(batch);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "提醒展示失败", e);
        }
    }

    private Map<Long, String> courseNames() {
        Map<Long, String> names = new HashMap<>();
        for (Course course : courseDao.findAll(true)) {
            names.put(course.id(), course.name());
        }
        return names;
    }

    /** 是否处于静音：手动静音优先，其次设置里的静音时段。 */
    public boolean isQuiet(LocalDateTime now) {
        LocalDateTime mute = mutedUntil;
        if (mute != null && now.isBefore(mute)) {
            return true;
        }
        return settings.inQuietWindow(now);
    }

    /** 手动静音一段时间（托盘菜单"静音 1 小时"）。 */
    public void muteFor(Duration duration) {
        mutedUntil = ClockProvider.now().plus(duration);
        LOG.info("已静音至 " + mutedUntil);
    }

    public void clearMute() {
        mutedUntil = null;
    }

    public LocalDateTime mutedUntil() {
        return mutedUntil;
    }

    public int deferredCount() {
        return deferred.size();
    }

    public void shutdown() {
        scheduler.shutdownNow();
        LOG.info("提醒调度已停止");
    }

    @Override
    public void close() {
        shutdown();
    }
}
