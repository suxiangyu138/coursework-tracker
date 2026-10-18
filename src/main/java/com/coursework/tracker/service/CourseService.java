package com.coursework.tracker.service;

import com.coursework.tracker.core.AppEvents;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.core.EventBus;
import com.coursework.tracker.dao.CourseDao;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.Semester;
import com.coursework.tracker.util.CourseNameMatcher;

import java.util.List;
import java.util.Optional;

/** 课程业务：校验、唯一性、增删改与事件发布。 */
public final class CourseService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_SEMESTER_LENGTH = 40;

    private final CourseDao courseDao;
    private final EventBus eventBus;

    public CourseService(CourseDao courseDao, EventBus eventBus) {
        this.courseDao = courseDao;
        this.eventBus = eventBus;
    }

    public List<Course> list() {
        return courseDao.findAll();
    }

    public Optional<Course> find(long id) {
        return courseDao.findById(id);
    }

    public Course create(String name, String code, double credit, String teacher,
                         String semester, Double targetScore) {
        validate(name, semester, credit, targetScore, 0L);
        Course draft = Course.draft(name, code, credit, teacher, semester, targetScore,
                ClockProvider.now());
        long id = courseDao.insert(draft);
        Course created = draft.withId(id);
        eventBus.publish(new AppEvents.CourseChanged());
        return created;
    }

    public Course update(Course course) {
        validate(course.name(), course.semester(), course.credit(), course.targetScore(), course.id());
        courseDao.update(course);
        eventBus.publish(new AppEvents.CourseChanged());
        return course;
    }

    public void delete(long id) {
        courseDao.delete(id);
        eventBus.publish(new AppEvents.CourseChanged());
    }

    public void setArchived(long id, boolean archived) {
        courseDao.setArchived(id, archived);
        eventBus.publish(new AppEvents.CourseChanged());
    }

    public long assignmentCount(long courseId) {
        return courseDao.countAssignments(courseId);
    }

    private void validate(String name, String semester, double credit, Double targetScore, long excludeId) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("课程名不能为空");
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            throw new BusinessException("课程名不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        String normalizedSemester = Semester.normalize(semester);
        if (normalizedSemester != null && normalizedSemester.length() > MAX_SEMESTER_LENGTH) {
            throw new BusinessException("学期名称不能超过 " + MAX_SEMESTER_LENGTH + " 个字符");
        }
        if (credit < 0 || credit > 20) {
            throw new BusinessException("学分应在 0–20 之间");
        }
        if (targetScore != null && (targetScore <= 0 || targetScore > 100)) {
            throw new BusinessException("目标分应在 1–100 之间");
        }
        courseDao.findByName(name.trim()).ifPresent(existing -> {
            if (existing.id() != excludeId) {
                throw new BusinessException(duplicateMessage(existing));
            }
        });
        // 数据库唯一约束区分大小写和空格；这里再用归一化名称兜底，
        // 避免「操作系统」和「操作系统 」「操作系统(实践)」这类近似重复。
        for (Course existing : courseDao.findAll(true)) {
            if (existing.id() == excludeId) {
                continue;
            }
            if (CourseNameMatcher.same(existing.name(), name)) {
                throw new BusinessException(duplicateMessage(existing));
            }
        }
    }

    private static String duplicateMessage(Course existing) {
        return "已存在同名课程：" + existing.name() + "（" + existing.semesterLabel()
                + "）。课程名需全局唯一，同一门课请直接复用。";
    }
}
