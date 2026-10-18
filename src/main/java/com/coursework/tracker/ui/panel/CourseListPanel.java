package com.coursework.tracker.ui.panel;

import com.coursework.tracker.model.CompletionStats;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseProgress;
import com.coursework.tracker.model.Semester;
import com.coursework.tracker.ui.ThemeManager;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 左侧课程列表：按学期分组显示每门课的完成率，点击筛选表格。
 *
 * <ul>
 *   <li>顶部「全部作业」= 不筛选；</li>
 *   <li>学期标题行 = 筛选该学期全部课程；</li>
 *   <li>课程行 = 只筛选这一门课；</li>
 *   <li>底部按钮 / 右键菜单 / 双击可新建、编辑、删除课程。</li>
 * </ul>
 */
public final class CourseListPanel extends JPanel {

    public enum Kind { ALL, SEMESTER, COURSE }

    /** 列表中的一行。ALL/SEMESTER 的 courseId 为 null。 */
    public record CourseRow(Kind kind, Long courseId, String title, String semester,
                            int courseCount, int total, int completed,
                            double countRate, double weightRate) {
    }

    private final DefaultListModel<CourseRow> listModel = new DefaultListModel<>();
    private final JList<CourseRow> list = new JList<>(listModel) {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = list.locationToIndex(event.getPoint());
            if (index < 0) {
                return null;
            }
            Rectangle bounds = list.getCellBounds(index, index);
            if (bounds == null || !bounds.contains(event.getPoint())) {
                return null;
            }
            CourseRow row = listModel.getElementAt(index);
            return switch (row.kind()) {
                case ALL -> "显示全部学期、全部课程";
                case SEMESTER -> Semester.display(row.semester()) + " · " + row.courseCount() + " 门课程";
                case COURSE -> row.title() + (row.semester() == null || row.semester().isBlank()
                        ? "" : " · " + Semester.display(row.semester()));
            };
        }
    };
    private final JLabel countLabel = new JLabel(" ");
    private final JButton newButton = new JButton("新建课程");
    private final JButton editButton = new JButton("编辑");
    private final JButton deleteButton = new JButton("删除");
    private JScrollPane listScroll;

    private Consumer<CourseFilter> onSelect = filter -> {
    };
    private Runnable onCreate = () -> {
    };
    private Consumer<Long> onEdit = id -> {
    };
    private Consumer<Long> onDelete = id -> {
    };
    private boolean updating;

    public CourseListPanel() {
        setLayout(new BorderLayout(0, 6));
        setOpaque(false);
        setPreferredSize(new Dimension(265, 0));

        add(buildHeader(), BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setToolTipText("");
        list.setCellRenderer(new RowRenderer());
        list.setFixedCellHeight(-1);
        list.setOpaque(false);
        list.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        list.addListSelectionListener(event -> {
            if (updating || event.getValueIsAdjusting()) {
                return;
            }
            CourseRow row = list.getSelectedValue();
            updateActions(row);
            if (row != null) {
                onSelect.accept(filterOf(row));
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    int index = indexAt(event.getPoint());
                    if (index >= 0) {
                        CourseRow row = listModel.getElementAt(index);
                        if (row.kind() == Kind.COURSE) {
                            onEdit.accept(row.courseId());
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                maybeShowPopup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                maybeShowPopup(event);
            }
        });
        bindKeys();

        JScrollPane scrollPane = new JScrollPane(list);
        listScroll = scrollPane;
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                fitListWidth();
            }
        });
        add(scrollPane, BorderLayout.CENTER);
        SwingUtilities.invokeLater(this::fitListWidth);

        add(buildActions(), BorderLayout.SOUTH);
        updateActions(null);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

        JLabel title = new JLabel("课程");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setToolTipText("按学期分组；点击学期标题可筛选该学期的作业");
        countLabel.setFont(countLabel.getFont().deriveFont(Font.PLAIN, 11f));
        countLabel.setForeground(ThemeManager.mutedText());
        header.add(title, BorderLayout.WEST);
        header.add(countLabel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actions.setOpaque(false);
        configureButton(newButton, "新建课程", "新建一门课程");
        configureButton(editButton, "编辑", "编辑选中的课程（也可以双击课程）");
        configureButton(deleteButton, "删除", "删除选中的课程；删除前会自动备份数据库");
        newButton.addActionListener(event -> onCreate.run());
        editButton.addActionListener(event -> {
            CourseRow row = list.getSelectedValue();
            if (row != null && row.kind() == Kind.COURSE) {
                onEdit.accept(row.courseId());
            }
        });
        deleteButton.addActionListener(event -> {
            CourseRow row = list.getSelectedValue();
            if (row != null && row.kind() == Kind.COURSE) {
                onDelete.accept(row.courseId());
            }
        });
        actions.add(newButton);
        actions.add(editButton);
        actions.add(deleteButton);
        return actions;
    }

    private static void configureButton(JButton button, String text, String tooltip) {
        button.setText(text);
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(3, 8, 3, 8));
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 12f));
    }

    private void bindKeys() {
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteCourse");
        list.getActionMap().put("deleteCourse", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                CourseRow row = list.getSelectedValue();
                if (row != null && row.kind() == Kind.COURSE) {
                    onDelete.accept(row.courseId());
                }
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "editCourse");
        list.getActionMap().put("editCourse", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                CourseRow row = list.getSelectedValue();
                if (row != null && row.kind() == Kind.COURSE) {
                    onEdit.accept(row.courseId());
                }
            }
        });
    }

    private void maybeShowPopup(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        int index = indexAt(event.getPoint());
        if (index >= 0) {
            list.setSelectedIndex(index);
        }
        CourseRow row = index >= 0 ? listModel.getElementAt(index) : null;

        JPopupMenu popup = new JPopupMenu();
        if (row != null && row.kind() == Kind.COURSE) {
            popup.add(menuItem("编辑课程…", () -> onEdit.accept(row.courseId())));
            popup.add(menuItem("删除课程", () -> onDelete.accept(row.courseId())));
            popup.addSeparator();
        }
        popup.add(menuItem("新建课程…", onCreate));
        popup.show(list, event.getX(), event.getY());
    }

    private static JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
    }

    private int indexAt(Point point) {
        int index = list.locationToIndex(point);
        if (index < 0) {
            return -1;
        }
        Rectangle bounds = list.getCellBounds(index, index);
        return bounds != null && bounds.contains(point) ? index : -1;
    }

    private void updateActions(CourseRow row) {
        boolean courseSelected = row != null && row.kind() == Kind.COURSE;
        editButton.setEnabled(courseSelected);
        deleteButton.setEnabled(courseSelected);
    }

    public void setOnSelect(Consumer<CourseFilter> handler) {
        this.onSelect = handler == null ? filter -> {
        } : handler;
    }

    public void setOnCreate(Runnable handler) {
        this.onCreate = handler == null ? () -> {
        } : handler;
    }

    public void setOnEdit(Consumer<Long> handler) {
        this.onEdit = handler == null ? id -> {
        } : handler;
    }

    public void setOnDelete(Consumer<Long> handler) {
        this.onDelete = handler == null ? id -> {
        } : handler;
    }

    /** 让每行宽度跟随列表视口，避免长课程名把横向滚动条撑出来。 */
    private void fitListWidth() {
        if (listScroll == null) {
            return;
        }
        int width = listScroll.getViewport().getWidth();
        if (width > 0 && list.getFixedCellWidth() != width) {
            list.setFixedCellWidth(width);
        }
    }

    /** 当前选中的课程 id；选中「全部作业」或学期标题时返回 null。 */
    public Long selectedCourseId() {
        CourseRow row = list.getSelectedValue();
        return row != null && row.kind() == Kind.COURSE ? row.courseId() : null;
    }

    /**
     * 重建课程列表，并尽量保持原来的筛选范围。
     *
     * @return 实际生效的筛选（原目标已不存在时回退到「全部作业」）
     */
    public CourseFilter setCourses(List<Course> courses, CompletionStats stats, CourseFilter requested) {
        updating = true;
        try {
            listModel.clear();
            listModel.addElement(new CourseRow(Kind.ALL, null, "全部作业", null,
                    courses.size(), stats.total(), stats.completed(),
                    stats.countRate(), stats.weightRate()));

            Map<String, List<Course>> groups = new LinkedHashMap<>();
            for (Course course : courses) {
                String semester = Semester.normalize(course.semester());
                groups.computeIfAbsent(semester == null ? "" : semester,
                        key -> new ArrayList<>()).add(course);
            }
            List<String> semesters = new ArrayList<>(groups.keySet());
            semesters.sort(CourseListPanel::compareSemesterKeys);

            Collator collator = Collator.getInstance(Locale.CHINA);
            int index = 1;
            int selectedIndex = 0;
            CourseFilter effective = CourseFilter.all();

            for (String semester : semesters) {
                List<Course> group = groups.get(semester);
                group.sort(Comparator.comparing(Course::name, collator));

                int total = 0;
                int completed = 0;
                for (Course course : group) {
                    CourseProgress progress = stats.byCourse().get(course.id());
                    if (progress != null) {
                        total += progress.total();
                        completed += progress.completed();
                    }
                }
                String title = semester.isBlank()
                        ? Semester.UNASSIGNED_LABEL : Semester.displayShort(semester);
                CourseRow header = new CourseRow(Kind.SEMESTER, null, title, semester,
                        group.size(), total, completed,
                        total == 0 ? 0 : (double) completed / total, 0);
                listModel.addElement(header);
                if (matchesSemester(requested, semester)) {
                    selectedIndex = index;
                    effective = semester.isBlank()
                            ? CourseFilter.unassigned() : CourseFilter.bySemester(semester);
                }
                index++;

                for (Course course : group) {
                    CourseProgress progress = stats.byCourse().get(course.id());
                    int courseTotal = progress == null ? 0 : progress.total();
                    int courseCompleted = progress == null ? 0 : progress.completed();
                    double countRate = progress == null ? 0 : progress.countRate();
                    double weightRate = progress == null ? 0 : progress.weightRate();
                    if (requested instanceof CourseFilter.SingleCourse single
                            && single.courseId() == course.id()) {
                        selectedIndex = index;
                        effective = requested;
                    }
                    listModel.addElement(new CourseRow(Kind.COURSE, course.id(), course.name(),
                            semester, 1, courseTotal, courseCompleted, countRate, weightRate));
                    index++;
                }
            }

            list.setSelectedIndex(selectedIndex);
            updateActions(list.getSelectedValue());
            countLabel.setText(courses.size() + " 门");
            fitListWidth();
            return effective;
        } finally {
            updating = false;
        }
    }

    private static boolean matchesSemester(CourseFilter filter, String semester) {
        if (filter instanceof CourseFilter.BySemester bySemester) {
            return bySemester.semester().equals(Semester.normalize(semester));
        }
        if (filter instanceof CourseFilter.Unassigned) {
            return Semester.normalize(semester) == null;
        }
        return false;
    }

    private static CourseFilter filterOf(CourseRow row) {
        return switch (row.kind()) {
            case ALL -> CourseFilter.all();
            case SEMESTER -> row.semester() == null || row.semester().isBlank()
                    ? CourseFilter.unassigned() : CourseFilter.bySemester(row.semester());
            case COURSE -> CourseFilter.singleCourse(row.courseId());
        };
    }

    /** 新学年在前；无法解析的自定义学期排在后面；未分学期永远最后。 */
    private static int compareSemesterKeys(String left, String right) {
        Optional<Semester> leftSemester = Semester.parse(left);
        Optional<Semester> rightSemester = Semester.parse(right);
        if (leftSemester.isPresent() && rightSemester.isPresent()) {
            return rightSemester.get().compareTo(leftSemester.get());
        }
        if (leftSemester.isPresent()) {
            return -1;
        }
        if (rightSemester.isPresent()) {
            return 1;
        }
        boolean leftBlank = left == null || left.isBlank();
        boolean rightBlank = right == null || right.isBlank();
        if (leftBlank && rightBlank) {
            return 0;
        }
        if (leftBlank) {
            return 1;
        }
        if (rightBlank) {
            return -1;
        }
        return left.compareTo(right);
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    private static final class RowRenderer implements ListCellRenderer<CourseRow> {

        private final CourseCell courseCell = new CourseCell();
        private final HeaderCell headerCell = new HeaderCell();

        @Override
        public Component getListCellRendererComponent(JList<? extends CourseRow> list,
                                                      CourseRow row, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            return row.kind() == Kind.SEMESTER
                    ? headerCell.render(row, isSelected)
                    : courseCell.render(list, row, index, isSelected);
        }
    }

    /** 普通行：「全部作业」或单门课程。 */
    private static final class CourseCell extends JPanel {

        private final JLabel nameLabel = new JLabel();
        private final JLabel percentLabel = new JLabel();
        private final JProgressBar progressBar = new JProgressBar(0, 100);

        CourseCell() {
            super(new BorderLayout(6, 3));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            setPreferredSize(new Dimension(0, 56));

            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12.5f));
            percentLabel.setFont(percentLabel.getFont().deriveFont(Font.PLAIN, 11f));
            percentLabel.setForeground(ThemeManager.mutedText());

            JPanel header = new JPanel(new BorderLayout(4, 0));
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

        Component render(JList<? extends CourseRow> list, CourseRow row, int index, boolean isSelected) {
            nameLabel.setText(row.title());
            int rate = (int) Math.round(row.countRate() * 100);
            progressBar.setValue(rate);
            boolean completed = row.total() > 0 && rate >= 100;
            progressBar.setForeground(completed ? new Color(0x2E7D32) : new Color(0x3B82F6));
            percentLabel.setText(row.total() == 0 ? "—" : rate + "%");
            nameLabel.setFont(nameLabel.getFont().deriveFont(
                    row.kind() == Kind.ALL ? Font.BOLD : Font.PLAIN, 12.5f));
            setToolTipText(row.kind() == Kind.ALL
                    ? "显示全部学期、全部课程"
                    : "共 " + row.total() + " 项作业 · 已完成 " + row.completed() + " 项");

            if (isSelected) {
                setBackground(new Color(0x3B82F6));
                nameLabel.setForeground(Color.WHITE);
                percentLabel.setForeground(new Color(0xDCE7FF));
            } else {
                setBackground(index % 2 == 1 ? ThemeManager.alternateRow() : ThemeManager.cardBackground());
                nameLabel.setForeground(list.getForeground());
                percentLabel.setForeground(ThemeManager.mutedText());
            }
            setOpaque(true);
            return this;
        }
    }

    /** 学期标题行。 */
    private static final class HeaderCell extends JPanel {

        private final JLabel titleLabel = new JLabel();
        private final JLabel metaLabel = new JLabel();

        HeaderCell() {
            super(new BorderLayout(4, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 3, 10));
            setPreferredSize(new Dimension(0, 36));
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11.5f));
            metaLabel.setFont(metaLabel.getFont().deriveFont(Font.PLAIN, 10.5f));
            add(titleLabel, BorderLayout.CENTER);
            add(metaLabel, BorderLayout.EAST);
        }

        Component render(CourseRow row, boolean isSelected) {
            titleLabel.setText(row.title());
            metaLabel.setText(row.courseCount() + " 门");
            setToolTipText(Semester.display(row.semester()) + " · 共 " + row.total() + " 项作业");
            if (isSelected) {
                setBackground(new Color(0x3B82F6));
                titleLabel.setForeground(Color.WHITE);
                metaLabel.setForeground(new Color(0xDCE7FF));
            } else {
                setBackground(ThemeManager.isDark() ? new Color(0x26292E) : new Color(0xEDF2F9));
                titleLabel.setForeground(ThemeManager.isDark()
                        ? new Color(0xC9D1DC) : new Color(0x334155));
                metaLabel.setForeground(ThemeManager.mutedText());
            }
            setOpaque(true);
            return this;
        }
    }
}
