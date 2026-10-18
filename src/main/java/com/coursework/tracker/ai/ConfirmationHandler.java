package com.coursework.tracker.ai;

import java.util.List;

/** 执行计划确认回调（在 EDT 上弹窗）。 */
@FunctionalInterface
public interface ConfirmationHandler {

    boolean confirm(List<String> steps);
}
