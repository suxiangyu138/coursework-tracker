package com.coursework.tracker.core;

import com.coursework.tracker.ai.AiActionExecutor;
import com.coursework.tracker.ai.AiAgentService;
import com.coursework.tracker.ai.DeepSeekClient;
import com.coursework.tracker.dao.AiChatDao;
import com.coursework.tracker.dao.AssignmentDao;
import com.coursework.tracker.dao.CourseDao;
import com.coursework.tracker.dao.Database;
import com.coursework.tracker.dao.ReminderLogDao;
import com.coursework.tracker.dao.SchemaMigrator;
import com.coursework.tracker.dao.SettingDao;
import com.coursework.tracker.service.AssignmentService;
import com.coursework.tracker.service.BackupService;
import com.coursework.tracker.service.CourseService;
import com.coursework.tracker.service.DemoDataFactory;
import com.coursework.tracker.service.GradeService;
import com.coursework.tracker.service.MutableReminderSink;
import com.coursework.tracker.service.ReminderService;
import com.coursework.tracker.service.Settings;
import com.coursework.tracker.service.StatisticsService;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 手写装配：持有数据库、DAO、Service 与事件总线，界面通过它取用。
 * 不引入任何 DI 框架，保持课程设计的可读性。
 */
public final class AppContext implements AutoCloseable {

    private final Database database;
    private final EventBus eventBus = new EventBus();
    private final CourseDao courseDao;
    private final AssignmentDao assignmentDao;
    private final ReminderLogDao reminderLogDao;
    private final SettingDao settingDao;
    private final Settings settings;
    private final CourseService courseService;
    private final AssignmentService assignmentService;
    private final StatisticsService statisticsService;
    private final GradeService gradeService;
    private final MutableReminderSink reminderSink = new MutableReminderSink();
    private final ReminderService reminderService;
    private final DemoDataFactory demoDataFactory;
    private final BackupService backupService;
    private final AiChatDao aiChatDao;
    private final AiAgentService aiAgentService;
    private final AtomicBoolean closed = new AtomicBoolean();

    public AppContext(Database database) {
        this.database = database;
        SchemaMigrator.migrate(database);

        this.courseDao = new CourseDao(database);
        this.assignmentDao = new AssignmentDao(database);
        this.reminderLogDao = new ReminderLogDao(database);
        this.settingDao = new SettingDao(database);
        this.settings = new Settings(settingDao);

        this.courseService = new CourseService(courseDao, eventBus);
        this.assignmentService = new AssignmentService(assignmentDao, courseDao, reminderLogDao, eventBus);
        this.statisticsService = new StatisticsService();
        this.gradeService = new GradeService(courseDao, assignmentDao);
        this.reminderService = new ReminderService(assignmentDao, courseDao, reminderLogDao,
                settings, reminderSink, eventBus);
        this.demoDataFactory = new DemoDataFactory(courseService, assignmentService);
        Path backupDir = (database.file() != null && database.file().getParent() != null)
                ? database.file().getParent().resolve("backup")
                : AppPaths.dataDir().resolve("backup");
        this.backupService = new BackupService(database, courseDao, assignmentDao, settingDao, backupDir);
        this.aiChatDao = new AiChatDao(database);
        this.aiAgentService = new AiAgentService(new DeepSeekClient(),
                new AiActionExecutor(courseService, assignmentService, backupService), aiChatDao);
    }

    public static AppContext open(Path databaseFile) {
        return new AppContext(Database.open(databaseFile));
    }

    public Database database() {
        return database;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public CourseDao courseDao() {
        return courseDao;
    }

    public AssignmentDao assignmentDao() {
        return assignmentDao;
    }

    public ReminderLogDao reminderLogDao() {
        return reminderLogDao;
    }

    public SettingDao settingDao() {
        return settingDao;
    }

    public Settings settings() {
        return settings;
    }

    public CourseService courseService() {
        return courseService;
    }

    public AssignmentService assignmentService() {
        return assignmentService;
    }

    public StatisticsService statisticsService() {
        return statisticsService;
    }

    public GradeService gradeService() {
        return gradeService;
    }

    public MutableReminderSink reminderSink() {
        return reminderSink;
    }

    public ReminderService reminderService() {
        return reminderService;
    }

    public DemoDataFactory demoDataFactory() {
        return demoDataFactory;
    }

    public BackupService backupService() {
        return backupService;
    }

    public AiChatDao aiChatDao() {
        return aiChatDao;
    }

    public AiAgentService aiAgentService() {
        return aiAgentService;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            reminderService.shutdown();
        } finally {
            database.close();
        }
    }
}
