package com.coursework.tracker.service;

import com.coursework.tracker.dao.AssignmentDao;
import com.coursework.tracker.dao.CourseDao;
import com.coursework.tracker.dao.Database;
import com.coursework.tracker.dao.SettingDao;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.util.Formatters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 数据备份：JSON 全量导出/导入、CSV 导出、每日自动备份（SQLite 在线快照）。
 *
 * <p>导入采用"整体覆盖"：先校验文件，再自动备份当前库，最后在一个事务里清空并写入，
 * 任何一步失败都会回滚，不会留下半套数据。
 */
public final class BackupService {

    private static final Logger LOG = Logger.getLogger(BackupService.class.getName());
    private static final String FORMAT = "coursework-tracker-backup";
    private static final int FORMAT_VERSION = 1;
    private static final int AUTO_BACKUP_KEEP = 7;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Database database;
    private final CourseDao courseDao;
    private final AssignmentDao assignmentDao;
    private final SettingDao settingDao;
    private final Path backupDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public BackupService(Database database, CourseDao courseDao, AssignmentDao assignmentDao,
                         SettingDao settingDao, Path backupDir) {
        this.database = database;
        this.courseDao = courseDao;
        this.assignmentDao = assignmentDao;
        this.settingDao = settingDao;
        this.backupDir = backupDir;
    }

    public record ExportResult(Path file, int courses, int assignments) {
    }

    public record ImportResult(int courses, int assignments, Path safetyBackup) {
    }

    // ------------------------------------------------------------------
    // 导出
    // ------------------------------------------------------------------

    public ExportResult exportJson(Path file) throws IOException {
        List<Course> courses = courseDao.findAll(true);
        List<Assignment> assignments = assignmentDao.findAll();
        Map<Long, String> courseNames = new HashMap<>();
        for (Course course : courses) {
            courseNames.put(course.id(), course.name());
        }

        List<CourseDto> courseDtos = new ArrayList<>();
        for (Course course : courses) {
            courseDtos.add(new CourseDto(course.name(), course.code(), course.credit(),
                    course.teacher(), course.targetScore(), course.archived(),
                    Formatters.db(course.createdAt())));
        }
        List<AssignmentDto> assignmentDtos = new ArrayList<>();
        for (Assignment assignment : assignments) {
            assignmentDtos.add(new AssignmentDto(
                    courseNames.getOrDefault(assignment.courseId(), ""),
                    assignment.title(), Formatters.db(assignment.dueAt()), assignment.weight(),
                    assignment.estimatedMinutes(), assignment.status().name(),
                    Formatters.db(assignment.submittedAt()), assignment.score(), assignment.notes(),
                    Formatters.db(assignment.createdAt()), Formatters.db(assignment.updatedAt())));
        }

        BackupFile backup = new BackupFile(FORMAT, FORMAT_VERSION,
                Formatters.db(LocalDateTime.now()), courseDtos, assignmentDtos);
        writeFile(file, gson.toJson(backup));
        return new ExportResult(file, courseDtos.size(), assignmentDtos.size());
    }

    public ExportResult exportCsv(Path file) throws IOException {
        List<Course> courses = courseDao.findAll(true);
        List<Assignment> assignments = assignmentDao.findAll();
        Map<Long, String> courseNames = new HashMap<>();
        for (Course course : courses) {
            courseNames.put(course.id(), course.name());
        }

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("课程,作业,截止时间,权重(%),预计耗时(分钟),状态,得分,备注\r\n");
        for (Assignment assignment : assignments) {
            csv.append(csv(courseNames.getOrDefault(assignment.courseId(), ""))).append(',')
                    .append(csv(assignment.title())).append(',')
                    .append(csv(Formatters.display(assignment.dueAt()))).append(',')
                    .append(assignment.weight()).append(',')
                    .append(assignment.estimatedMinutes() == null ? "" : assignment.estimatedMinutes()).append(',')
                    .append(csv(assignment.status().label())).append(',')
                    .append(assignment.score() == null ? "" : assignment.score()).append(',')
                    .append(csv(assignment.notes())).append("\r\n");
        }
        writeFile(file, csv.toString());
        return new ExportResult(file, courses.size(), assignments.size());
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void writeFile(Path file, String content) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // 导入
    // ------------------------------------------------------------------

    public ImportResult importJson(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        BackupFile backup;
        try {
            backup = gson.fromJson(text, BackupFile.class);
        } catch (RuntimeException e) {
            throw new IOException("不是有效的 JSON 文件：" + e.getMessage(), e);
        }
        if (backup == null || backup.courses == null || backup.assignments == null) {
            throw new IOException("备份文件缺少 courses / assignments 字段");
        }
        if (!FORMAT.equals(backup.format)) {
            throw new IOException("不是作业哨兵的备份文件（format=" + backup.format + "）");
        }
        if (backup.version > FORMAT_VERSION) {
            throw new IOException("备份版本过新（v" + backup.version + "），请升级程序后再导入");
        }

        List<Course> courses = new ArrayList<>();
        Map<String, Course> byName = new HashMap<>();
        for (CourseDto dto : backup.courses) {
            String name = requireName(dto.name(), "课程名");
            if (byName.containsKey(name)) {
                throw new IOException("备份中存在重名课程：" + name);
            }
            Course course = new Course(0L, name, dto.code(), dto.credit(), dto.teacher(),
                    dto.targetScore(), dto.archived(),
                    parseTime(dto.createdAt(), LocalDateTime.now()));
            courses.add(course);
            byName.put(name, course);
        }

        List<Assignment> assignments = new ArrayList<>();
        List<String> assignmentCourseNames = new ArrayList<>();
        for (AssignmentDto dto : backup.assignments) {
            String courseName = requireName(dto.course(), "作业所属课程");
            Course course = byName.get(courseName);
            if (course == null) {
                throw new IOException("作业「" + dto.title() + "」引用了不存在的课程：" + courseName);
            }
            AssignmentStatus status = parseStatus(dto.status());
            LocalDateTime dueAt = parseTime(dto.dueAt(), null);
            if (dueAt == null) {
                throw new IOException("作业「" + dto.title() + "」缺少截止时间");
            }
            if (dueAt.getYear() < 2000 || dueAt.getYear() > 2100) {
                throw new IOException("作业「" + dto.title() + "」的截止时间超出 2000–2100 年");
            }
            if (dto.weight() < 0 || dto.weight() > 100) {
                throw new IOException("作业「" + dto.title() + "」的权重超出 0–100");
            }
            if (dto.score() != null && (dto.score() < 0 || dto.score() > 100)) {
                throw new IOException("作业「" + dto.title() + "」的得分超出 0–100");
            }
            assignments.add(new Assignment(0L, 0L, requireName(dto.title(), "作业标题"), dueAt,
                    dto.weight(), dto.estimatedMinutes(), status,
                    parseTime(dto.submittedAt(), null), dto.score(), dto.notes(),
                    parseTime(dto.createdAt(), LocalDateTime.now()),
                    parseTime(dto.updatedAt(), LocalDateTime.now())));
            assignmentCourseNames.add(courseName);
        }

        Path safety = backupNow();
        int[] counts;
        try {
            counts = database.transaction(connection -> {
                try (var st = connection.createStatement()) {
                    st.execute("DELETE FROM reminder_log");
                    st.execute("DELETE FROM assignment");
                    st.execute("DELETE FROM course");
                    st.execute("DELETE FROM sqlite_sequence WHERE name IN ('course','assignment','reminder_log')");
                }
                Map<String, Long> ids = new HashMap<>();
                for (Course course : courses) {
                    ids.put(course.name(), insertCourse(connection, course));
                }
                for (int i = 0; i < assignments.size(); i++) {
                    Long courseId = ids.get(assignmentCourseNames.get(i));
                    if (courseId == null) {
                        throw new IllegalStateException("无法匹配课程：" + assignmentCourseNames.get(i));
                    }
                    insertAssignment(connection, assignments.get(i).withCourseId(courseId));
                }
                return new int[]{courses.size(), assignments.size()};
            });
        } catch (RuntimeException e) {
            throw new IOException("导入失败，已回滚：" + e.getMessage(), e);
        }

        LOG.info("导入完成：课程 " + counts[0] + " 门，作业 " + counts[1] + " 条，导入前备份 " + safety);
        return new ImportResult(counts[0], counts[1], safety);
    }

    private long insertCourse(java.sql.Connection connection, Course course) throws java.sql.SQLException {
        try (var ps = connection.prepareStatement(
                "INSERT INTO course(name, code, credit, teacher, target_score, archived, created_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, course.name());
            ps.setString(2, course.code());
            ps.setDouble(3, course.credit());
            ps.setString(4, course.teacher());
            if (course.targetScore() == null) {
                ps.setObject(5, null);
            } else {
                ps.setDouble(5, course.targetScore());
            }
            ps.setInt(6, course.archived() ? 1 : 0);
            ps.setString(7, Formatters.db(course.createdAt()));
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new java.sql.SQLException("插入课程未获得主键");
    }

    private void insertAssignment(java.sql.Connection connection, Assignment assignment)
            throws java.sql.SQLException {
        try (var ps = connection.prepareStatement(
                "INSERT INTO assignment(course_id, title, due_at, weight, estimated_minutes, status, "
                        + "submitted_at, score, notes, created_at, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, assignment.courseId());
            ps.setString(2, assignment.title());
            ps.setString(3, Formatters.db(assignment.dueAt()));
            ps.setDouble(4, assignment.weight());
            if (assignment.estimatedMinutes() == null) {
                ps.setObject(5, null);
            } else {
                ps.setInt(5, assignment.estimatedMinutes());
            }
            ps.setString(6, assignment.status().name());
            ps.setString(7, Formatters.db(assignment.submittedAt()));
            if (assignment.score() == null) {
                ps.setObject(8, null);
            } else {
                ps.setDouble(8, assignment.score());
            }
            ps.setString(9, assignment.notes());
            ps.setString(10, Formatters.db(assignment.createdAt()));
            ps.setString(11, Formatters.db(assignment.updatedAt()));
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // 备份
    // ------------------------------------------------------------------

    /** 立即备份一份当前数据库到 backup 目录。 */
    public Path backupNow() {
        String stamp = FILE_TIME.format(LocalDateTime.now());
        Path target = backupDir.resolve("coursework-manual-" + stamp + ".db");
        return database.backupTo(target);
    }

    /** 每天首次启动自动备份；当天已备份则返回 null。 */
    public Path autoBackupIfNeeded(LocalDateTime now) {
        String today = now.toLocalDate().toString();
        if (today.equals(settingDao.get("backup.lastDate", ""))) {
            return null;
        }
        Path target = backupDir.resolve("coursework-" + today.replace("-", "") + ".db");
        database.backupTo(target);
        settingDao.set("backup.lastDate", today);
        pruneAutoBackups();
        LOG.info("自动备份完成: " + target);
        return target;
    }

    /** 备份文件列表，最新在前。 */
    public List<Path> listBackups() {
        if (!Files.isDirectory(backupDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(backupDir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".db"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void pruneAutoBackups() {
        if (!Files.isDirectory(backupDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(backupDir)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().matches("coursework-\\d{8}\\.db"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (int i = 0; i < files.size() - AUTO_BACKUP_KEEP; i++) {
                Files.deleteIfExists(files.get(i));
            }
        } catch (IOException e) {
            LOG.warning("清理旧备份失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 校验与工具
    // ------------------------------------------------------------------

    private static String requireName(String value, String field) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException(field + "不能为空");
        }
        return value.trim();
    }

    private static AssignmentStatus parseStatus(String value) throws IOException {
        if (value == null) {
            return AssignmentStatus.TODO;
        }
        try {
            return AssignmentStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("未知的作业状态：" + value);
        }
    }

    private static LocalDateTime parseTime(String value, LocalDateTime fallback) throws IOException {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Formatters.parseDb(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("无法解析时间：" + value);
        }
    }

    // ------------------------------------------------------------------
    // DTO
    // ------------------------------------------------------------------

    private record BackupFile(String format, int version, String exportedAt,
                              List<CourseDto> courses, List<AssignmentDto> assignments) {
    }

    private record CourseDto(String name, String code, double credit, String teacher,
                             Double targetScore, boolean archived, String createdAt) {
    }

    private record AssignmentDto(String course, String title, String dueAt, double weight,
                                 Integer estimatedMinutes, String status, String submittedAt,
                                 Double score, String notes, String createdAt, String updatedAt) {
    }
}
