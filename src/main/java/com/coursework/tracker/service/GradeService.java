package com.coursework.tracker.service;

import com.coursework.tracker.dao.AssignmentDao;
import com.coursework.tracker.dao.CourseDao;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseGrade;
import com.coursework.tracker.model.GradeScale;
import com.coursework.tracker.util.GradeCalculator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 成绩业务：组织课程与作业数据，计算交给纯函数 {@link GradeCalculator}。 */
public final class GradeService {

    private final CourseDao courseDao;
    private final AssignmentDao assignmentDao;
    private final GradeScale scale = GradeScale.STANDARD_4;

    public GradeService(CourseDao courseDao, AssignmentDao assignmentDao) {
        this.courseDao = courseDao;
        this.assignmentDao = assignmentDao;
    }

    public List<CourseGrade> computeAll() {
        return compute(courseDao.findAll(), assignmentDao.findAll());
    }

    public List<CourseGrade> compute(List<Course> courses, List<Assignment> assignments) {
        Map<Long, List<Assignment>> byCourse = assignments.stream()
                .collect(Collectors.groupingBy(Assignment::courseId));
        return courses.stream()
                .map(course -> GradeCalculator.compute(course,
                        byCourse.getOrDefault(course.id(), List.of()), scale))
                .toList();
    }

    public double gpa(List<CourseGrade> grades) {
        return GradeCalculator.gpa(grades);
    }

    public GradeScale scale() {
        return scale;
    }
}
