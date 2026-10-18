package com.coursework.tracker.model;

/** 作业状态机：未开始 → 进行中 → 已完成 / 已提交，允许回退。 */
public enum AssignmentStatus {

    TODO("未开始", "○"),
    DOING("进行中", "◐"),
    DONE("已完成", "●"),
    SUBMITTED("已提交", "✓");

    private final String label;
    private final String symbol;

    AssignmentStatus(String label, String symbol) {
        this.label = label;
        this.symbol = symbol;
    }

    public String label() {
        return label;
    }

    public String symbol() {
        return symbol;
    }

    /** DONE 与 SUBMITTED 都视为"已完成"，不再提醒。 */
    public boolean isCompleted() {
        return this == DONE || this == SUBMITTED;
    }

    public static AssignmentStatus fromDb(String value) {
        if (value == null) {
            return TODO;
        }
        for (AssignmentStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return TODO;
    }

    @Override
    public String toString() {
        return label;
    }
}
