package com.coursework.tracker.ui.panel;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.CompletionStats;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseGrade;
import com.coursework.tracker.model.CourseProgress;
import com.coursework.tracker.ui.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.util.List;

/** 底部统计区：6 个指标卡 + 两张图表。 */
public final class DashboardPanel extends JPanel {

    private final AppContext context;
    private final StatCard todayCard = new StatCard("今日截止");
    private final StatCard weekCard = new StatCard("本周截止");
    private final StatCard overdueCard = new StatCard("已逾期");
    private final StatCard completionCard = new StatCard("完成率");
    private final StatCard weightCard = new StatCard("权重完成度");
    private final StatCard gpaCard = new StatCard("预计 GPA");

    private final JPanel charts = new JPanel(new GridLayout(1, 2, 10, 0));
    private final List<StatCard> cards = List.of(todayCard, weekCard, overdueCard,
            completionCard, weightCard, gpaCard);

    public DashboardPanel(AppContext context) {
        this.context = context;
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JPanel cards = new JPanel(new GridLayout(1, 6, 10, 0));
        cards.setOpaque(false);
        cards.add(todayCard);
        cards.add(weekCard);
        cards.add(overdueCard);
        cards.add(completionCard);
        cards.add(weightCard);
        cards.add(gpaCard);
        add(cards, BorderLayout.NORTH);

        charts.setOpaque(false);
        charts.setPreferredSize(new Dimension(10, 230));
        add(charts, BorderLayout.CENTER);
    }

    public void refresh(List<Course> courses, List<Assignment> assignments) {
        LocalDateTime now = ClockProvider.now();
        CompletionStats stats = context.statisticsService().compute(courses, assignments);
        List<CourseGrade> grades = context.gradeService().compute(courses, assignments);
        double gpa = context.gradeService().gpa(grades);

        todayCard.setValue(String.valueOf(
                context.statisticsService().dueToday(assignments, now.toLocalDate())));
        weekCard.setValue(String.valueOf(
                context.statisticsService().dueWithinDays(assignments, now, 7)));
        overdueCard.setValue(String.valueOf(
                context.statisticsService().overdueCount(assignments, now)));
        completionCard.setValue(stats.countRateText());
        weightCard.setValue(stats.weightRateText());
        gpaCard.setValue(gpa > 0 ? String.format("%.2f", gpa) : "—");

        boolean hasData = stats.total() > 0;
        charts.removeAll();
        if (hasData) {
            charts.add(ChartFactory.workloadPanel(
                    context.statisticsService().workload(assignments, now, 14)));
            charts.add(ChartFactory.coursePanel(stats.byCourse().values()));
        } else {
            charts.add(ChartFactory.emptyChartPanel("还没有作业数据，点击「新建作业」开始"));
            charts.add(ChartFactory.emptyChartPanel("录入作业后这里会显示课程完成率"));
        }
        charts.revalidate();
        charts.repaint();
    }

    /** 主题切换后刷新卡片配色。 */
    public void applyTheme() {
        for (StatCard card : cards) {
            card.applyTheme();
        }
        repaint();
    }

    /** 单个指标卡。 */
    private static final class StatCard extends JPanel {

        private final JLabel valueLabel = new JLabel("0");

        StatCard(String title) {
            setLayout(new BorderLayout());
            setOpaque(true);
            setBackground(ThemeManager.cardBackground());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setForeground(ThemeManager.mutedText());
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 12f));

            valueLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
            valueLabel.setForeground(ThemeManager.isDark() ? Color.WHITE : new Color(0x1F2937));

            add(titleLabel, BorderLayout.NORTH);
            add(valueLabel, BorderLayout.CENTER);
        }

        void setValue(String text) {
            valueLabel.setText(text);
        }

        void applyTheme() {
            setBackground(ThemeManager.cardBackground());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));
            valueLabel.setForeground(ThemeManager.isDark() ? Color.WHITE : new Color(0x1F2937));
        }
    }

    /** 供状态栏复用的完成度文本。 */
    public String summaryText(List<Course> courses, List<Assignment> assignments) {
        CompletionStats stats = context.statisticsService().compute(courses, assignments);
        return stats.completed() + "/" + stats.total() + " 已完成";
    }
}
