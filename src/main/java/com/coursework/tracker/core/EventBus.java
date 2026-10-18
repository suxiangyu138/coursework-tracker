package com.coursework.tracker.core;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 极简类型化事件总线：数据变化后通知界面刷新，避免"改库又改 UI"的双写不一致。
 * 所有监听器都在 EDT 上被调用。
 */
public final class EventBus {

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    public <T> void subscribe(Class<T> type, Consumer<T> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void publish(Object event) {
        if (event == null) {
            return;
        }
        List<Consumer<?>> consumers = listeners.get(event.getClass());
        if (consumers == null || consumers.isEmpty()) {
            return;
        }
        Runnable task = () -> dispatch(event, consumers);
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    @SuppressWarnings("unchecked")
    private static void dispatch(Object event, List<Consumer<?>> consumers) {
        for (Consumer<?> consumer : consumers) {
            try {
                ((Consumer<Object>) consumer).accept(event);
            } catch (RuntimeException ex) {
                // 单个监听器异常不应影响其他监听器
                java.util.logging.Logger.getLogger(EventBus.class.getName())
                        .warning("事件监听器异常: " + ex);
            }
        }
    }
}
