package com.coursework.tracker.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemesterTest {

    @Test
    @DisplayName("学期编码与中文标签")
    void codeAndLabel() {
        Semester semester = new Semester(2026, 1);

        assertEquals("2026-2027-1", semester.code());
        assertEquals("2026–2027 学年 · 第一学期（秋）", semester.label());
        assertEquals("2026–2027 秋", semester.shortLabel());
        assertEquals("2026–2027 学年 · 第二学期（春）", new Semester(2026, 2).label());
    }

    @Test
    @DisplayName("按日期推断当前学期：8 月–次年 1 月为秋季，2–7 月为春季")
    void currentSemesterInference() {
        assertEquals("2026-2027-1", Semester.current(LocalDateTime.of(2026, 10, 18, 12, 0)).code());
        assertEquals("2026-2027-1", Semester.current(LocalDateTime.of(2026, 8, 1, 0, 0)).code());
        assertEquals("2026-2027-1", Semester.current(LocalDateTime.of(2027, 1, 15, 0, 0)).code());
        assertEquals("2026-2027-2", Semester.current(LocalDateTime.of(2027, 3, 1, 0, 0)).code());
        assertEquals("2025-2026-2", Semester.current(LocalDateTime.of(2026, 7, 1, 0, 0)).code());
    }

    @Test
    @DisplayName("解析规范编码，非法值返回 empty")
    void parse() {
        assertEquals(new Semester(2026, 1), Semester.parse(" 2026-2027-1 ").orElseThrow());
        assertTrue(Semester.parse(null).isEmpty());
        assertTrue(Semester.parse("").isEmpty());
        assertTrue(Semester.parse("2026-2028-1").isEmpty());
        assertTrue(Semester.parse("2026-2027-3").isEmpty());
        assertTrue(Semester.parse("2026 秋").isEmpty());
        assertTrue(Semester.parse("自定义学期").isEmpty());
    }

    @Test
    @DisplayName("显示：编码转长标签，空值显示未分学期，自定义文本原样显示")
    void display() {
        assertEquals("2026–2027 学年 · 第一学期（秋）", Semester.display("2026-2027-1"));
        assertEquals(Semester.UNASSIGNED_LABEL, Semester.display(null));
        assertEquals(Semester.UNASSIGNED_LABEL, Semester.display("   "));
        assertEquals("短学期", Semester.display("短学期"));
        assertEquals("短学期", Semester.displayShort("短学期"));
    }

    @Test
    @DisplayName("上一学期与排序")
    void previousAndOrdering() {
        Semester autumn = new Semester(2026, 1);
        Semester spring = new Semester(2026, 2);

        assertEquals(new Semester(2025, 2), autumn.previous());
        assertEquals(autumn, spring.previous());
        assertTrue(spring.compareTo(autumn) > 0);
        assertTrue(autumn.compareTo(new Semester(2025, 2)) > 0);

        List<Semester> sorted = List.of(autumn, new Semester(2025, 2), spring).stream()
                .sorted(Semester.NEWEST_FIRST)
                .toList();
        assertEquals(List.of(spring, autumn, new Semester(2025, 2)), sorted);
    }
}
