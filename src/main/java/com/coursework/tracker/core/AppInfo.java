package com.coursework.tracker.core;

/**
 * 产品与作者信息。版本优先读取 jar manifest 的 Implementation-Version，
 * 开发态（target/classes）回退到常量，避免两处不一致。
 */
public final class AppInfo {

    public static final String NAME = "作业哨兵";
    public static final String ENGLISH_NAME = "CourseWorkTracker";
    public static final String VERSION = "1.2.0";

    public static final String AUTHOR = "苏巷雨";
    public static final String AUTHOR_ALIAS = "suxiangyu";
    public static final String EMAIL = "suxiangyu_dev@foxmail.com";

    public static final String GITHUB = "https://github.com/suxiangyu138/coursework-tracker";
    public static final String GITEE = "https://gitee.com/suxiangyu138/coursework-tracker";
    public static final String GITHUB_RELEASES = GITHUB + "/releases";
    public static final String GITEE_RELEASES = GITEE + "/releases";

    private AppInfo() {
    }

    public static String version() {
        Package info = AppInfo.class.getPackage();
        String manifestVersion = info == null ? null : info.getImplementationVersion();
        return manifestVersion == null || manifestVersion.isBlank() ? VERSION : manifestVersion;
    }

    public static String title() {
        return NAME + " · " + ENGLISH_NAME;
    }

    public static String runtime() {
        return "Java " + System.getProperty("java.version")
                + "  ·  " + System.getProperty("os.name")
                + " " + System.getProperty("os.arch");
    }
}
