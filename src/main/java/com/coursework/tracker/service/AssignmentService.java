package com.coursework.tracker.service;

import com.coursework.tracker.core.AppEvents;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.core.EventBus;
import com.coursework.tracker.dao.AssignmentDao;
import com.coursework.tracker.dao.CourseDao;
import com.coursework.tracker.dao.ReminderLogDao;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 作业业务：校验、状态流转、提醒记录维护与事件发布。 */
public final class AssignmentService {

    private static final LocalDateTime MIN_DUE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DUE = LocalDateTime.of(2100, 1, 1, 0, 0);

    private final AssignmentDao assignmentDao;
    private final CourseDao courseDao;
    private final ReminderLogDao reminderLogDao;
    private final EventBus eventBus;

    public AssignmentService(AssignmentDao assignmentDao, CourseDao courseDao,
                             ReminderLogDao reminderLogDao, EventBus eventBus) {
        this.assignmentDao = assignmentDao;
        this.courseDao = courseDao;
        this.reminderLogDao = reminderLogDao;
        this.eventBus = eventBus;
    }

    public List<Assignment> listAll() {
        return assignmentDao.findAll();
    }

    public List<Assignment> listByCourse(long courseId) {
        return assignmentDao.findByCourse(courseId);
    }

    public Assignment create(Assignment draft) {
        validate(draft, 0L);
        LocalDateTime now = ClockProvider.now();
        Assignment toInsert = draft.withUpdatedAt(now);
        long id = assignmentDao.insert(toInsert);
        Assignment created = toInsert.withId(id);
        eventBus.publish(new AppEvents.AssignmentChanged());
        return created;
    }

    public Assignment update(Assignment assignment) {
        Assignment existing = assignmentDao.findById(assignment.id())
                .orElseThrow(() -> new BusinessException("作业不存在: " + assignment.id()));
        validate(assignment, assignment.id());

        LocalDateTime now = ClockProvider.now();
        Assignment updated = assignment.withUpdatedAt(now);
        assignmentDao.update(updated);

        if (!Objects.equals(existing.dueAt(), updated.dueAt())) {
            // 截止时间变了，历史提醒记录失效，重新排程（BR-05）
            reminderLogDao.deleteByAssignment(updated.id());
        }
        eventBus.publish(new AppEvents.AssignmentChanged());
        return updated;
    }

    public void delete(long id) {
        assignmentDao.delete(id);
        eventBus.publish(new AppEvents.AssignmentChanged());
    }

    /** 状态流转：完成时记录提交时间，重新打开时清空。 */
    public Assignment changeStatus(long id, AssignmentStatus status) {
        Assignment existing = assignmentDao.findById(id)
                .orElseThrow(() -> new BusinessException("作业不存在: " + id));
        LocalDateTime now = ClockProvider.now();
        LocalDateTime submittedAt = status.isCompleted() ? now : null;
        Assignment updated = existing.withStatus(status)
                .withSubmittedAt(submittedAt)
                .withUpdatedAt(now);
        assignmentDao.update(updated);
        if (!status.isCompleted()) {
            // 重新打开后，逾期汇总应允许再次提醒
            reminderLogDao.deleteByAssignment(id);
        }
        eventBus.publish(new AppEvents.AssignmentChanged());
        return updated;
    }

    public Assignment setScore(long id, Double score) {
        if (score != null && (score < 0 || score > 100)) {
            throw new BusinessException("得分应在 0–100 之间");
        }
        Assignment existing = assignmentDao.findById(id)
                .orElseThrow(() -> new BusinessException("作业不存在: " + id));
        Assignment updated = existing.withScore(score)
                .withUpdatedAt(ClockProvider.now());
        assignmentDao.update(updated);
        eventBus.publish(new AppEvents.AssignmentChanged());
        return updated;
    }

    private void validate(Assignment assignment, long excludeId) {
        if (assignment.title() == null || assignment.title().isBlank()) {
            throw new BusinessException("作业标题不能为空");
        }
        if (assignment.title().trim().length() > 100) {
            throw new BusinessException("作业标题不能超过 100 个字符");
        }
        if (assignment.notes() != null && assignment.notes().length() > 500) {
            throw new BusinessException("备注不能超过 500 个字符");
        }
        if (assignment.weight() < 0 || assignment.weight() > 100) {
            throw new BusinessException("权重应在 0–100% 之间");
        }
        if (assignment.estimatedMinutes() != null && assignment.estimatedMinutes() < 0) {
            throw new BusinessException("预计耗时不能为负");
        }
        LocalDateTime dueAt = assignment.dueAt();
        if (dueAt == null || dueAt.isBefore(MIN_DUE) || dueAt.isAfter(MAX_DUE)) {
            throw new BusinessException("截止时间应在 2000–2100 年之间");
        }
        if (courseDao.findById(assignment.courseId()).isEmpty()) {
            throw new BusinessException("作业所属课程不存在，请先选择课程");
        }
    }
}
