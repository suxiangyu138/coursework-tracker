package com.coursework.tracker.model;

/** (作业, 规则) 唯一键，用于内存去重。 */
public record FiredKey(long assignmentId, String ruleCode) {

    public static FiredKey of(long assignmentId, ReminderRule rule) {
        return new FiredKey(assignmentId, rule.code());
    }
}
