package com.coursework.tracker.ui.panel;

import com.coursework.tracker.model.CourseProgress;
import com.coursework.tracker.model.DayBucket;
import com.coursework.tracker.ui.ThemeManager;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Collection;
import java.util.List;

/** JFreeChart 图表构建：统一样式、透明背景、跟随主题。 */
public final class ChartFactory {

    private ChartFactory() {
    }

    public static ChartPanel workloadPanel(List<DayBucket> buckets) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (DayBucket bucket : buckets) {
            dataset.addValue(bucket.count(), "作业数", bucket.label());
        }
        JFreeChart chart = org.jfree.chart.ChartFactory.createBarChart(
                "未来 14 天待完成作业", null, null, dataset);
        style(chart);
        chart.removeLegend();
        chart.getCategoryPlot().getDomainAxis()
                .setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        return panel(chart);
    }

    public static ChartPanel coursePanel(Collection<CourseProgress> progress) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (CourseProgress item : progress) {
            if (item.total() > 0) {
                dataset.addValue(item.countRate() * 100, "完成率", item.courseName());
            }
        }
        JFreeChart chart = org.jfree.chart.ChartFactory.createBarChart(
                "各课程完成率（%）", null, null, dataset);
        style(chart);
        chart.removeLegend();
        chart.getCategoryPlot().getDomainAxis()
                .setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        NumberAxis range = (NumberAxis) chart.getCategoryPlot().getRangeAxis();
        range.setRange(0, 100);
        range.setAutoRangeIncludesZero(true);
        return panel(chart);
    }

    private static void style(JFreeChart chart) {
        Color background = ThemeManager.cardBackground();
        Color grid = ThemeManager.cardBorder();
        chart.setBackgroundPaint(background);
        chart.setBorderVisible(false);
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            chart.getTitle().setPaint(ThemeManager.isDark() ? new Color(0xE0E0E0) : new Color(0x263238));
        }
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(background);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(grid);
        plot.setDomainGridlinesVisible(false);
        plot.getDomainAxis().setTickLabelFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
        plot.getDomainAxis().setLabelFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        plot.getRangeAxis().setTickLabelFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
        plot.getDomainAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setAxisLineVisible(false);

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        renderer.setSeriesPaint(0, new Color(0x3B82F6));
        renderer.setSeriesPaint(1, new Color(0x10B981));
        renderer.setMaximumBarWidth(0.12);
        renderer.setDefaultItemLabelsVisible(false);
    }

    private static ChartPanel panel(JFreeChart chart) {
        ChartPanel panel = new ChartPanel(chart);
        panel.setBackground(ThemeManager.cardBackground());
        panel.setOpaque(true);
        panel.setPreferredSize(new Dimension(320, 200));
        panel.setMinimumSize(new Dimension(200, 150));
        panel.setMouseZoomable(false);
        panel.setPopupMenu(null);
        panel.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true));
        return panel;
    }

    /** 供占位使用：空数据时展示提示文字。 */
    public static JPanel emptyChartPanel(String message) {
        JPanel panel = new JPanel();
        panel.setBackground(ThemeManager.cardBackground());
        panel.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true));
        panel.add(new javax.swing.JLabel(message));
        return panel;
    }
}
