package com.coursework.tracker.service;

import com.coursework.tracker.core.AppEvents;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.core.EventBus;
import com.coursework.tracker.dao.CourseDao;
import com.coursework.tracker.model.Course;

import java.util.List;
import java.util.Optional;

/** 课程业务：校验、唯一性、增删改与事件发布。 */
public final class CourseService {

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

    public Course create(String name, String code, double credit, String teacher, Double targetScore) {
        validate(name, credit, targetScore, 0L);
        Course draft = Course.draft(name, code, credit, teacher, targetScore, ClockProvider.now());
        long id = courseDao.insert(draft);
        Course created = draft.withId(id);
        eventBus.publish(new AppEvents.CourseChanged());
        return created;
    }

    public Course update(Course course) {
        validate(course.name(), course.credit(), course.targetScore(), course.id());
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

    private void validate(String name, double credit, Double targetScore, long excludeId) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("课程名不能为空");
        }
        if (name.trim().length() > 50) {
            throw new BusinessException("课程名不能超过 50 个字符");
        }
        if (credit < 0 || credit > 20) {
            throw new BusinessException("学分应在 0–20 之间");
        }
        if (targetScore != null && (targetScore <= 0 || targetScore > 100)) {
            throw new BusinessException("目标分应在 1–100 之间");
        }
        courseDao.findByName(name.trim()).ifPresent(existing -> {
            if (existing.id() != excludeId) {
                throw new BusinessException("已存在同名课程：" + name.trim());
            }
        });
    }
}
