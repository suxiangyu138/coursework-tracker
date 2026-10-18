package com.coursework.tracker.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/** 文件锁实现的单实例保护：避免两个托盘图标与两套提醒。 */
public final class SingleInstanceGuard implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SingleInstanceGuard.class.getName());

    private final FileChannel channel;
    private final FileLock lock;
    private final boolean acquired;
    private boolean closed;

    private SingleInstanceGuard(FileChannel channel, FileLock lock, boolean acquired) {
        this.channel = channel;
        this.lock = lock;
        this.acquired = acquired;
    }

    public static SingleInstanceGuard acquire(Path lockFile) {
        FileChannel channel = null;
        try {
            Files.createDirectories(lockFile.getParent());
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                LOG.info("检测到已有实例在运行");
                return new SingleInstanceGuard(null, null, false);
            }
            channel.truncate(0);
            channel.write(ByteBuffer.wrap(String.valueOf(ProcessHandle.current().pid()).getBytes()));
            return new SingleInstanceGuard(channel, lock, true);
        } catch (OverlappingFileLockException e) {
            closeQuietly(channel);
            return new SingleInstanceGuard(null, null, false);
        } catch (IOException e) {
            LOG.warning("单实例锁获取失败，继续启动: " + e.getMessage());
            closeQuietly(channel);
            return new SingleInstanceGuard(null, null, false);
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // 忽略
            }
        }
    }

    public boolean isAcquired() {
        return acquired;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException e) {
            LOG.warning("释放文件锁失败: " + e.getMessage());
        }
        closeQuietly(channel);
    }
}
