package com.coursework.tracker.ui.panel;

import com.coursework.tracker.model.CompletionStats;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseProgress;
import com.coursework.tracker.ui.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;

/** 左侧课程列表：显示每门课的完成率，点击筛选表格。 */
public final class CourseListPanel extends JPanel {

    /** id 为 null 表示"全部作业"。 */
    public record CourseRow(Long id, String name, int total, int completed,
                            double countRate, double weightRate) {
    }

    private final DefaultListModel<CourseRow> listModel = new DefaultListModel<>();
    private final JList<CourseRow> list = new JList<>(listModel);
    private Consumer<Long> onSelect = id -> {
    };
    private boolean updating;

    public CourseListPanel() {
        setLayout(new BorderLayout(0, 6));
        setOpaque(false);
        setPreferredSize(new Dimension(240, 0));

        JLabel title = new JLabel("课程");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        add(title, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new RowRenderer());
        list.setFixedCellHeight(56);
        list.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        add(scrollPane, BorderLayout.CENTER);

        list.addListSelectionListener(event -> {
            if (updating || event.getValueIsAdjusting()) {
                return;
            }
            CourseRow row = list.getSelectedValue();
            if (row != null) {
                onSelect.accept(row.id());
            }
        });
    }

    public void setOnSelect(Consumer<Long> handler) {
        this.onSelect = handler == null ? id -> {
        } : handler;
    }

    public void setCourses(List<Course> courses, CompletionStats stats, Long selectedId) {
        updating = true;
        listModel.clear();
        listModel.addElement(new CourseRow(null, "全部作业", stats.total(), stats.completed(),
                stats.countRate(), stats.weightRate()));

        int selectedIndex = 0;
        int index = 1;
        for (Course course : courses) {
            CourseProgress progress = stats.byCourse().get(course.id());
            int total = progress == null ? 0 : progress.total();
            int completed = progress == null ? 0 : progress.completed();
            double rate = progress == null ? 0 : progress.countRate();
            double weightRate = progress == null ? 0 : progress.weightRate();
            listModel.addElement(new CourseRow(course.id(), course.name(), total, completed,
                    rate, weightRate));
            if (selectedId != null && selectedId.equals(course.id())) {
                selectedIndex = index;
            }
            index++;
        }
        list.setSelectedIndex(selectedIndex);
        updating = false;
    }

    private static final class RowRenderer extends JPanel implements ListCellRenderer<CourseRow> {

        private final JLabel nameLabel = new JLabel();
        private final JLabel percentLabel = new JLabel();
        private final JProgressBar progressBar = new JProgressBar(0, 100);

        RowRenderer() {
            setLayout(new BorderLayout(6, 3));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12.5f));
            percentLabel.setFont(percentLabel.getFont().deriveFont(Font.PLAIN, 11f));
            percentLabel.setForeground(ThemeManager.mutedText());

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.add(nameLabel, BorderLayout.CENTER);
            header.add(percentLabel, BorderLayout.EAST);

            progressBar.setStringPainted(false);
            progressBar.setPreferredSize(new Dimension(100, 6));
            progressBar.setBorderPainted(false);
            progressBar.setOpaque(false);

            add(header, BorderLayout.NORTH);
            add(progressBar, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CourseRow> list,
                                                      CourseRow row, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(row.name());
            int rate = (int) Math.round(row.countRate() * 100);
            progressBar.setValue(rate);
            progressBar.setForeground(rate >= 100 ? new Color(0x2E7D32) : new Color(0x3B82F6));
            percentLabel.setText(row.total() == 0 ? "—" : rate + "%");

            Color background;
            if (isSelected) {
                background = new Color(0x3B82F6);
                nameLabel.setForeground(Color.WHITE);
                percentLabel.setForeground(new Color(0xDCE7FF));
            } else {
                background = index % 2 == 1 ? ThemeManager.alternateRow() : ThemeManager.cardBackground();
                nameLabel.setForeground(list.getForeground());
                percentLabel.setForeground(ThemeManager.mutedText());
            }
            setBackground(background);
            setOpaque(true);
            return this;
        }
    }
}
