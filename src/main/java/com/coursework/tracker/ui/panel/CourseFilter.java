package com.coursework.tracker.ui.panel;

import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.Semester;

/**
 * 课程列表当前筛选范围：全部 / 某学期 / 未分学期 / 单门课程。
 * 作为不可变值对象在界面与表格过滤器之间传递。
 */
public sealed interface CourseFilter {

    boolean matches(Course course);

    static CourseFilter all() {
        return new All();
    }

    static CourseFilter bySemester(String semester) {
        return new BySemester(semester);
    }

    static CourseFilter unassigned() {
        return new Unassigned();
    }

    static CourseFilter singleCourse(long courseId) {
        return new SingleCourse(courseId);
    }

    /** 全部课程。 */
    record All() implements CourseFilter {
        @Override
        public boolean matches(Course course) {
            return true;
        }
    }

    /** 某个学期下的全部课程。 */
    record BySemester(String semester) implements CourseFilter {
        public BySemester {
            if (Semester.normalize(semester) == null) {
                throw new IllegalArgumentException("学期不能为空");
            }
            semester = Semester.normalize(semester);
        }

        @Override
        public boolean matches(Course course) {
            return semester.equals(Semester.normalize(course.semester()));
        }
    }

    /** 还没有分配学期的课程。 */
    record Unassigned() implements CourseFilter {
        @Override
        public boolean matches(Course course) {
            return Semester.normalize(course.semester()) == null;
        }
    }

    /** 单门课程。 */
    record SingleCourse(long courseId) implements CourseFilter {
        @Override
        public boolean matches(Course course) {
            return course.id() == courseId;
        }
    }
}
