package com.coursework.tracker.model;

/** 百分制成绩到绩点的映射策略（不同学校规则不同，做成可替换）。 */
public interface GradeScale {

    /**
     * @param finalScore 百分制总评，0–100
     * @return 绩点
     */
    double toGradePoint(double finalScore);

    String displayName();

    /** 常见院校的"标准 4.0"映射。 */
    GradeScale STANDARD_4 = new GradeScale() {
        @Override
        public double toGradePoint(double score) {
            if (score >= 90) {
                return 4.0;
            } else if (score >= 85) {
                return 3.7;
            } else if (score >= 82) {
                return 3.3;
            } else if (score >= 78) {
                return 3.0;
            } else if (score >= 75) {
                return 2.7;
            } else if (score >= 72) {
                return 2.3;
            } else if (score >= 68) {
                return 2.0;
            } else if (score >= 64) {
                return 1.5;
            } else if (score >= 60) {
                return 1.0;
            }
            return 0.0;
        }

        @Override
        public String displayName() {
            return "标准 4.0";
        }
    };
}
