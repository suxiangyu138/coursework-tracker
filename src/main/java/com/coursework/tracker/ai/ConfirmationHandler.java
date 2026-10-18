package com.coursework.tracker.ai;

import java.util.List;

/** 写操作执行前的用户确认回调（在 EDT 上弹窗）。 */
@FunctionalInterface
public interface ConfirmationHandler {

    boolean confirm(List<String> previews);
}
