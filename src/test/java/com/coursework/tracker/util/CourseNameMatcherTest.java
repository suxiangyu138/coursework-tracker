package com.coursework.tracker.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseNameMatcherTest {

    @Test
    @DisplayName("归一化处理空格、全半角、大小写与连接符")
    void normalize() {
        assertEquals("操作系统(实验)", CourseNameMatcher.normalize(" 操作系统（实验） "));
        assertEquals("数据结构", CourseNameMatcher.normalize("数据 结构"));
        assertEquals("c++程序设计", CourseNameMatcher.normalize("C＋＋程序设计"));
        assertEquals("软件工程实验", CourseNameMatcher.normalize("软件工程—实验"));
        assertEquals("", CourseNameMatcher.normalize(null));
        assertEquals("", CourseNameMatcher.normalize("   "));
    }

    @Test
    @DisplayName("同一门课的不同写法视为相同")
    void same() {
        assertTrue(CourseNameMatcher.same("操作系统（实验）", "操作系统(实验)"));
        assertTrue(CourseNameMatcher.same("数据结构", "数据 结构"));
        assertTrue(CourseNameMatcher.same("C++程序设计", "C＋＋程序设计"));
        assertTrue(CourseNameMatcher.same("软件工程-实验", "软件工程实验"));
        assertFalse(CourseNameMatcher.same("操作系统", "操作系统实验"));
        assertFalse(CourseNameMatcher.same("操作系统", "操作系统（实践）"));
        assertFalse(CourseNameMatcher.same("", ""));
    }

    @Test
    @DisplayName("相似度能识别一两个字的差别，但不把无关课程判为相似")
    void similarity() {
        assertEquals(1.0, CourseNameMatcher.similarity("数据结构", "数据 结构"), 0.0001);
        assertTrue(CourseNameMatcher.similarity("操作系统", "操作系统实验") >= 0.55);
        assertTrue(CourseNameMatcher.similarity("数字图像处理", "数字图像处理（双语）") >= 0.55);
        assertTrue(CourseNameMatcher.similarity("高等数学A", "高等数学B") >= 0.8);
        assertTrue(CourseNameMatcher.similarity("操作系统", "大学英语") < 0.3);
        assertEquals(0.0, CourseNameMatcher.similarity("", "操作系统"), 0.0001);
    }
}
