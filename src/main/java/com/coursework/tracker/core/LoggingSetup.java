package com.coursework.tracker.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** 日志初始化：写入 logs/app.log，按大小滚动保留 7 个文件。 */
public final class LoggingSetup {

    private LoggingSetup() {
    }

    public static void init(Path logDir) {
        try {
            Files.createDirectories(logDir);
            Logger root = Logger.getLogger("");
            root.setLevel(Level.INFO);
            for (java.util.logging.Handler handler : root.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            FileHandler fileHandler = new FileHandler(
                    logDir.resolve("app.log").toString(), 5_000_000, 1, true);
            fileHandler.setEncoding("UTF-8");
            fileHandler.setLevel(Level.INFO);
            fileHandler.setFormatter(new SimpleFormatter());
            root.addHandler(fileHandler);
        } catch (IOException e) {
            System.err.println("日志初始化失败: " + e.getMessage());
        }
    }
}
