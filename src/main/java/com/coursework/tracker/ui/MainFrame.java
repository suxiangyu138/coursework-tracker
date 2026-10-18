package com.coursework.tracker.ui;

import com.coursework.tracker.core.AppContext;
import com.coursework.tracker.core.AppEvents;
import com.coursework.tracker.core.AppInfo;
import com.coursework.tracker.core.AppPaths;
import com.coursework.tracker.core.ClockProvider;
import com.coursework.tracker.model.Assignment;
import com.coursework.tracker.model.AssignmentStatus;
import com.coursework.tracker.model.CompletionStats;
import com.coursework.tracker.model.Countdown;
import com.coursework.tracker.model.Course;
import com.coursework.tracker.model.CourseGrade;
import com.coursework.tracker.model.PlannedReminder;
import com.coursework.tracker.model.Urgency;
import com.coursework.tracker.service.BusinessException;
import com.coursework.tracker.ui.dialog.AboutDialog;
import com.coursework.tracker.ui.dialog.AiChatDialog;
import com.coursework.tracker.ui.dialog.AssignmentDialog;
import com.coursework.tracker.ui.dialog.CourseDialog;
import com.coursework.tracker.ui.dialog.GradeDetailDialog;
import com.coursework.tracker.ui.dialog.ReminderCenterDialog;
import com.coursework.tracker.ui.dialog.SettingsDialog;
import com.coursework.tracker.ui.dialog.UpdateDialog;
import com.coursework.tracker.ui.panel.CourseListPanel;
import com.coursework.tracker.ui.panel.DashboardPanel;
import com.coursework.tracker.ui.table.AssignmentTableModel;
import com.coursework.tracker.ui.table.CountdownRenderer;
import com.coursework.tracker.ui.table.PlainRenderers;
import com.coursework.tracker.ui.table.StatusRenderer;
import com.coursework.tracker.ui.table.UrgencyRenderer;
import com.coursework.tracker.util.Formatters;
import com.coursework.tracker.util.TimeUtils;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 主窗口：工具栏 + 课程侧栏 + 作业表 + 统计区 + 状态栏。 */
public final class MainFrame extends JFrame {

    private static final Logger LOG = Logger.getLogger(MainFrame.class.getName());
    private static final String VERSION = "1.0.0";

    /** 排序维度。 */
    public enum SortMode {
        URGENCY("紧急度"),
        DUE("截止时间"),
        WEIGHT("权重"),
        COURSE("课程");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final AppContext context;
    private final AssignmentTableModel tableModel = new AssignmentTableModel();
    private final JTable table;
    private final TableRowSorter<AssignmentTableModel> sorter = new TableRowSorter<>(tableModel);
    private final CourseListPanel coursePanel = new CourseListPanel();
    private final DashboardPanel dashboard;

    private final JComboBox<SortMode> sortCombo = new JComboBox<>(SortMode.values());
    private final JTextField searchField = new JTextField(14);
    private final JLabel statusLeft = new JLabel(" ");
    private final JLabel statusRight = new JLabel(" ");
    private final Timer secondTimer;

    private TrayController tray;
    private JPanel statusBar;
    private Long selectedCourseId;
    private List<Course> courses = List.of();
    private List<Assignment> assignments = List.of();
    private Map<Long, String> courseNames = new HashMap<>();
    private int tickCount;

    public MainFrame(AppContext context) {
        super("作业哨兵 · CourseWorkTracker");
        this.context = context;
        this.dashboard = new DashboardPanel(context);
        AppIcon.applyTo(this);
        this.table = new JTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent event) {
                return buildTooltip(event);
            }
        };

        buildUi();
        bindEvents();
        refreshAll();

        secondTimer = new Timer(1000, e -> onSecondTick());
        secondTimer.start();

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                SwingUtilities.invokeLater(MainFrame.this::scrollTableToTop);
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCloseRequested();
            }
        });
        setSize(1180, 790);
        setMinimumSize(new Dimension(960, 620));
        setLocationRelativeTo(null);
    }

    public void installTray() {
        tray = new TrayController(this, context);
        if (tray.install()) {
            context.reminderSink().setDelegate(tray::notifyReminders);
            updateTrayTooltip(null);
        }
    }

    public boolean trayAvailable() {
        return tray != null && tray.isInstalled();
    }

    // ------------------------------------------------------------------
    // 界面搭建
    // ------------------------------------------------------------------

    private void buildUi() {
        setJMenuBar(buildMenuBar());

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
        root.add(buildToolbar(), BorderLayout.NORTH);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder(), 1, true));
        tableScroll.getViewport().setBackground(table.getBackground());

        JPanel tablePanel = new JPanel(new BorderLayout(0, 6));
        tablePanel.setOpaque(false);
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, dashboard);
        vertical.setResizeWeight(0.58);
        vertical.setDividerLocation(385);
        vertical.setBorder(null);
        vertical.setOpaque(false);
        right.add(vertical, BorderLayout.CENTER);

        JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, coursePanel, right);
        horizontal.setResizeWeight(0);
        horizontal.setDividerLocation(245);
        horizontal.setBorder(null);
        horizontal.setOpaque(false);
        root.add(horizontal, BorderLayout.CENTER);

        add(root, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        configureTable();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("文件");
        file.add(menuItem("新建作业", "control N", e -> newAssignment()));
        file.add(menuItem("新建课程", null, e -> newCourse()));
        file.addSeparator();
        file.add(menuItem("导出 JSON 备份…", null, e -> exportJsonBackup()));
        file.add(menuItem("导出 CSV 作业清单…", null, e -> exportCsv()));
        file.add(menuItem("导入备份…", null, e -> importBackup()));
        file.add(menuItem("立即备份数据", null, e -> backupNow()));
        file.addSeparator();
        file.add(menuItem("生成示例数据", null, e -> generateDemoData()));
        file.add(menuItem("打开数据目录", null, e -> openDataFolder()));
        file.addSeparator();
        file.add(menuItem("退出", "control Q", e -> exitApplication()));
        bar.add(file);

        JMenu view = new JMenu("视图");
        view.add(menuItem("刷新", "F5", e -> refreshAll()));
        view.add(menuItem("设置", "control COMMA", e -> openSettings()));
        view.add(menuItem("课程成绩预估", null, e -> showGradeDetail()));
        bar.add(view);

        JMenu reminder = new JMenu("提醒");
        reminder.add(menuItem("提醒中心", "control R", e -> showReminderCenter()));
        reminder.add(menuItem("立即检查", null, e -> checkRemindersNow()));
        reminder.add(menuItem("测试提醒", null, e -> testNotification()));
        reminder.addSeparator();
        reminder.add(menuItem("静音 1 小时", null, e -> {
            context.reminderService().muteFor(Duration.ofHours(1));
            showToast("已静音 1 小时");
        }));
        reminder.add(menuItem("取消静音", null, e -> {
            context.reminderService().clearMute();
            showToast("已恢复提醒");
        }));
        bar.add(reminder);

        JMenu ai = new JMenu("AI");
        ai.add(menuItem("AI 助手…", "control shift A", e -> AiChatDialog.show(this, context)));
        ai.add(menuItem("AI 设置…", null, e -> openSettings()));
        bar.add(ai);

        JMenu help = new JMenu("帮助");
        help.add(menuItem("关于与作者…", "F1", e -> AboutDialog.show(this, context)));
        help.add(menuItem("检查更新…", "control U", e -> UpdateDialog.show(this, context)));
        bar.add(help);
        return bar;
    }

    private JMenuItem menuItem(String text, String accelerator, java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        }
        item.addActionListener(listener);
        return item;
    }

    private JToolBar buildToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        JButton create = new JButton("+ 新建作业");
        create.setFont(create.getFont().deriveFont(Font.BOLD));
        create.addActionListener(e -> newAssignment());
        toolbar.add(create);

        JButton refresh = new JButton("刷新");
        refresh.addActionListener(e -> refreshAll());
        toolbar.add(refresh);
        toolbar.addSeparator(new Dimension(12, 0));

        toolbar.add(new JLabel("排序 "));
        sortCombo.setSelectedItem(SortMode.URGENCY);
        sortCombo.setMaximumSize(new Dimension(120, 30));
        sortCombo.addActionListener(e -> applySort());
        toolbar.add(sortCombo);

        toolbar.addSeparator(new Dimension(12, 0));
        toolbar.add(new JLabel("搜索 "));
        searchField.setMaximumSize(new Dimension(200, 30));
        searchField.putClientProperty("JTextField.placeholderText", "课程或作业名");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                sortAgain();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                sortAgain();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                sortAgain();
            }
        });
        toolbar.add(searchField);

        toolbar.add(javax.swing.Box.createHorizontalGlue());
        JButton aiButton = new JButton("AI 助手");
        aiButton.addActionListener(e -> AiChatDialog.show(this, context));
        toolbar.add(aiButton);
        JButton settings = new JButton("设置");
        settings.addActionListener(e -> openSettings());
        toolbar.add(settings);
        return toolbar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(true);
        bar.setBackground(UIManager.getColor("Panel.background"));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.cardBorder()),
                BorderFactory.createEmptyBorder(5, 4, 2, 4)));
        statusLeft.setFont(statusLeft.getFont().deriveFont(Font.PLAIN, 12f));
        statusRight.setFont(statusRight.getFont().deriveFont(Font.PLAIN, 12f));
        statusLeft.setForeground(ThemeManager.mutedText());
        statusRight.setForeground(ThemeManager.mutedText());
        bar.add(statusLeft, BorderLayout.WEST);
        bar.add(statusRight, BorderLayout.EAST);
        statusBar = bar;
        return bar;
    }

    private void configureTable() {
        table.setRowHeight(32);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(ThemeManager.cardBorder());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(sorter);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));

        TableColumn status = table.getColumnModel().getColumn(0);
        status.setPreferredWidth(78);
        status.setMinWidth(70);
        status.setMaxWidth(96);
        status.setCellRenderer(new StatusRenderer());

        TableColumn course = table.getColumnModel().getColumn(1);
        course.setPreferredWidth(100);
        course.setMinWidth(80);
        course.setCellRenderer(new PlainRenderers.CourseName());

        TableColumn title = table.getColumnModel().getColumn(2);
        title.setPreferredWidth(240);
        title.setMinWidth(140);
        title.setCellRenderer(new PlainRenderers.Title());

        TableColumn due = table.getColumnModel().getColumn(3);
        due.setPreferredWidth(132);
        due.setMinWidth(110);
        due.setCellRenderer(new PlainRenderers.DueDate());

        TableColumn countdown = table.getColumnModel().getColumn(4);
        countdown.setPreferredWidth(124);
        countdown.setMinWidth(108);
        countdown.setCellRenderer(new CountdownRenderer());

        TableColumn weight = table.getColumnModel().getColumn(5);
        weight.setPreferredWidth(58);
        weight.setMinWidth(52);
        weight.setMaxWidth(72);
        weight.setCellRenderer(new PlainRenderers.Weight());

        TableColumn urgency = table.getColumnModel().getColumn(6);
        urgency.setPreferredWidth(96);
        urgency.setMinWidth(88);
        urgency.setCellRenderer(new UrgencyRenderer());

        sorter.setComparator(0, Comparator.comparingInt(value -> ((AssignmentStatus) value).ordinal()));
        sorter.setComparator(3, Comparator.comparing(value -> (LocalDateTime) value));
        sorter.setComparator(4, Comparator.comparing(value -> (LocalDateTime) value));
        sorter.setComparator(5, Comparator.comparingDouble(value -> ((Number) value).doubleValue()));
        sorter.setComparator(6, Comparator.comparingDouble(value -> ((Number) value).doubleValue()));
        sorter.setRowFilter(new javax.swing.RowFilter<>() {
            @Override
            public boolean include(Entry<? extends AssignmentTableModel, ? extends Integer> entry) {
                return matches(tableModel.assignmentAt(entry.getIdentifier()));
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedAt(e);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }
        });

        table.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        table.getActionMap().put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelected();
            }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle");
        table.getActionMap().put("toggle", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleSelectedStatus();
            }
        });

        applySort();
    }

    private void maybeShowPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0) {
            return;
        }
        table.setRowSelectionInterval(row, row);
        Assignment assignment = selectedAssignment();
        if (assignment == null) {
            return;
        }
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        popup.add(menuItem("编辑", null, ev -> editAssignment(assignment)));
        popup.add(menuItem(assignment.isCompleted() ? "标记未开始" : "标记已完成", null,
                ev -> toggleStatus(assignment)));
        popup.add(menuItem("查看课程成绩", null,
                ev -> courses.stream().filter(c -> c.id() == assignment.courseId()).findFirst()
                        .ifPresent(course -> GradeDetailDialog.show(this, context, course))));
        popup.addSeparator();
        popup.add(menuItem("删除", null, ev -> deleteAssignment(assignment)));
        popup.show(table, e.getX(), e.getY());
    }

    private void openSelectedAt(MouseEvent e) {
        int row = table.rowAtPoint(e.getPoint());
        int column = table.columnAtPoint(e.getPoint());
        if (row < 0) {
            return;
        }
        Assignment assignment = tableModel.assignmentAt(table.convertRowIndexToModel(row));
        if (column == 0) {
            toggleStatus(assignment);
        } else {
            editAssignment(assignment);
        }
    }

    // ------------------------------------------------------------------
    // 数据刷新
    // ------------------------------------------------------------------

    private void bindEvents() {
        context.eventBus().subscribe(AppEvents.AssignmentChanged.class, event -> refreshAll());
        context.eventBus().subscribe(AppEvents.CourseChanged.class, event -> refreshAll());
        coursePanel.setOnSelect(id -> {
            selectedCourseId = id;
            sortAgain();
            CompletionStats stats = context.statisticsService().compute(courses, assignments);
            coursePanel.setCourses(courses, stats, selectedCourseId);
            SwingUtilities.invokeLater(this::scrollTableToTop);
        });
    }

    /** 让表格回到第一行（首次显示、切换筛选后）。 */
    private void scrollTableToTop() {
        if (table.getRowCount() > 0) {
            table.scrollRectToVisible(table.getCellRect(0, 0, true));
        }
    }

    public void refreshAll() {
        try {
            courses = context.courseService().list();
            assignments = context.assignmentService().listAll();
            courseNames = new HashMap<>();
            for (Course course : courses) {
                courseNames.put(course.id(), course.name());
            }

            tableModel.setScoring(context.settings().horizonHours(), context.settings().weights());
            tableModel.setData(assignments, courseNames);

            CompletionStats stats = context.statisticsService().compute(courses, assignments);
            coursePanel.setCourses(courses, stats, selectedCourseId);
            dashboard.refresh(courses, assignments);
            updateStatus(stats);
            sortAgain();
            LOG.fine(() -> "刷新完成：作业=" + assignments.size()
                    + " 模型行=" + tableModel.getRowCount()
                    + " 视图行=" + table.getRowCount()
                    + " 可见区域=" + table.getVisibleRect());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "刷新数据失败", e);
            showToast("刷新失败：" + e.getMessage());
        }
    }

    private void applySort() {
        if (sorter == null) {
            return;
        }
        SortMode mode = (SortMode) sortCombo.getSelectedItem();
        if (mode == null) {
            mode = SortMode.URGENCY;
        }
        List<javax.swing.RowSorter.SortKey> keys = switch (mode) {
            case URGENCY -> List.of(new javax.swing.RowSorter.SortKey(6,
                    javax.swing.SortOrder.DESCENDING));
            case DUE -> List.of(new javax.swing.RowSorter.SortKey(3,
                    javax.swing.SortOrder.ASCENDING));
            case WEIGHT -> List.of(new javax.swing.RowSorter.SortKey(5,
                    javax.swing.SortOrder.DESCENDING));
            case COURSE -> List.of(
                    new javax.swing.RowSorter.SortKey(1, javax.swing.SortOrder.ASCENDING),
                    new javax.swing.RowSorter.SortKey(3, javax.swing.SortOrder.ASCENDING));
        };
        sorter.setSortKeys(keys);
        sorter.sort();
    }

    private void sortAgain() {
        if (sorter != null) {
            sorter.sort();
        }
    }

    private boolean matches(Assignment assignment) {
        if (selectedCourseId != null && assignment.courseId() != selectedCourseId) {
            return false;
        }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return true;
        }
        String title = assignment.title().toLowerCase(Locale.ROOT);
        String course = courseNames.getOrDefault(assignment.courseId(), "").toLowerCase(Locale.ROOT);
        return title.contains(query) || course.contains(query);
    }

    private void onSecondTick() {
        tableModel.setNow(ClockProvider.now());
        tickCount++;
        if (tickCount % 60 == 0) {
            refreshAll();
            updateTrayTooltip(context.statisticsService().compute(courses, assignments));
        }
    }

    private void updateStatus(CompletionStats stats) {
        int overdue = context.statisticsService().overdueCount(assignments, ClockProvider.now());
        statusLeft.setText(String.format("共 %d 条作业 · 已完成 %d · 逾期 %d · 权重完成度 %s",
                stats.total(), stats.completed(), overdue, stats.weightRateText()));
        statusRight.setText("数据：" + AppPaths.databaseFile() + "  ·  v" + AppInfo.version());
    }

    private void updateTrayTooltip(CompletionStats stats) {
        if (tray == null || !tray.isInstalled()) {
            return;
        }
        Assignment nearest = assignments.stream()
                .filter(a -> !a.isCompleted())
                .min(Comparator.comparing(Assignment::dueAt))
                .orElse(null);
        String text = "作业哨兵 · 共 " + assignments.size() + " 条作业";
        if (nearest != null) {
            Countdown countdown = TimeUtils.countdown(ClockProvider.now(), nearest.dueAt());
            text += " · 最近：" + nearest.title() + "（" + countdown.display() + "）";
        }
        tray.updateTooltip(text);
    }

    // ------------------------------------------------------------------
    // 操作
    // ------------------------------------------------------------------

    private void newAssignment() {
        if (courses.isEmpty()) {
            Course course = CourseDialog.showDialog(this, context, null);
            if (course == null) {
                JOptionPane.showMessageDialog(this, "请先创建一门课程。", "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            refreshAll();
        }
        Assignment created = AssignmentDialog.showDialog(this, context, null);
        if (created != null) {
            showToast("已保存：" + created.title());
        }
    }

    private void newCourse() {
        Course created = CourseDialog.showDialog(this, context, null);
        if (created != null) {
            selectedCourseId = created.id();
            refreshAll();
        }
    }

    private void editAssignment(Assignment assignment) {
        Assignment updated = AssignmentDialog.showDialog(this, context, assignment);
        if (updated != null) {
            showToast("已更新：" + updated.title());
        }
    }

    private void toggleSelectedStatus() {
        Assignment assignment = selectedAssignment();
        if (assignment != null) {
            toggleStatus(assignment);
        }
    }

    private void toggleStatus(Assignment assignment) {
        AssignmentStatus next = assignment.isCompleted() ? AssignmentStatus.TODO : AssignmentStatus.DONE;
        context.assignmentService().changeStatus(assignment.id(), next);
    }

    private void deleteSelected() {
        Assignment assignment = selectedAssignment();
        if (assignment != null) {
            deleteAssignment(assignment);
        }
    }

    private void deleteAssignment(Assignment assignment) {
        int choice = JOptionPane.showConfirmDialog(this,
                "确定删除作业「" + assignment.title() + "」？\n删除后无法恢复，其提醒记录也会一并删除。",
                "删除作业", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            context.assignmentService().delete(assignment.id());
            showToast("已删除：" + assignment.title());
        }
    }

    private Assignment selectedAssignment() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return tableModel.assignmentAt(table.convertRowIndexToModel(viewRow));
    }

    private void generateDemoData() {
        int choice = JOptionPane.showConfirmDialog(this,
                "将生成 8 门课程、20 条示例作业（含逾期与已完成样例），是否继续？",
                "生成示例数据", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            int count = context.demoDataFactory().generate(ClockProvider.now());
            refreshAll();
            JOptionPane.showMessageDialog(this, "已生成 " + count + " 条示例作业。", "完成",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (BusinessException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "生成失败",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void openSettings() {
        SettingsDialog.show(this, context, this::onThemeChanged);
    }

    private void onThemeChanged() {
        ThemeManager.applyTo(this);
        dashboard.applyTheme();
        if (statusBar != null) {
            statusBar.setBackground(UIManager.getColor("Panel.background"));
        }
        refreshAll();
        repaint();
    }

    private void showGradeDetail() {
        if (courses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "还没有课程，请先新建课程。", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Course selected = courses.stream()
                .filter(c -> selectedCourseId != null && c.id() == selectedCourseId)
                .findFirst()
                .orElse(courses.get(0));
        Course choice = (Course) JOptionPane.showInputDialog(this, "选择要查看的课程：", "成绩预估",
                JOptionPane.QUESTION_MESSAGE, null, courses.toArray(), selected);
        if (choice != null) {
            GradeDetailDialog.show(this, context, choice);
        }
    }

    public void checkRemindersNow() {
        try {
            List<PlannedReminder> fired = context.reminderService().tick(ClockProvider.now());
            if (fired.isEmpty()) {
                showToast("检查完成：当前没有需要提醒的作业");
            } else {
                JOptionPane.showMessageDialog(this,
                        "本次触发 " + fired.size() + " 条提醒，已通过托盘气泡展示。",
                        "提醒检查", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "手动检查提醒失败", e);
            showToast("检查提醒失败：" + e.getMessage());
        }
    }

    private void testNotification() {
        if (tray != null) {
            tray.testNotification();
        } else {
            JOptionPane.showMessageDialog(this, "当前系统不支持托盘通知。", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ------------------------------------------------------------------
    // 备份与提醒中心
    // ------------------------------------------------------------------

    private void exportJsonBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出 JSON 备份");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON 备份 (*.json)", "json"));
        chooser.setSelectedFile(new File("coursework-backup-" + LocalDate.now() + ".json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = ensureExtension(chooser.getSelectedFile().toPath(), ".json");
        try {
            var result = context.backupService().exportJson(file);
            JOptionPane.showMessageDialog(this,
                    "已导出 " + result.courses() + " 门课程、" + result.assignments() + " 条作业：\n"
                            + result.file(),
                    "导出完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            showBackupError("导出失败", e);
        }
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出 CSV 作业清单");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 文件 (*.csv)", "csv"));
        chooser.setSelectedFile(new File("作业清单-" + LocalDate.now() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = ensureExtension(chooser.getSelectedFile().toPath(), ".csv");
        try {
            var result = context.backupService().exportCsv(file);
            JOptionPane.showMessageDialog(this,
                    "已导出 " + result.assignments() + " 条作业：\n" + result.file(),
                    "导出完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            showBackupError("导出 CSV 失败", e);
        }
    }

    private void importBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导入 JSON 备份");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON 备份 (*.json)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        int confirm = JOptionPane.showConfirmDialog(this,
                "导入会覆盖当前全部课程与作业。\n导入前会自动备份当前数据，确定继续吗？",
                "导入备份", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            var result = context.backupService().importJson(file);
            selectedCourseId = null;
            refreshAll();
            JOptionPane.showMessageDialog(this,
                    "导入完成：" + result.courses() + " 门课程、" + result.assignments() + " 条作业。\n\n"
                            + "导入前备份：\n" + result.safetyBackup(),
                    "导入完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            showBackupError("导入失败", e);
        }
    }

    private void backupNow() {
        try {
            Path file = context.backupService().backupNow();
            showToast("已备份：" + file.getFileName());
            JOptionPane.showMessageDialog(this, "备份完成：\n" + file, "备份数据",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            showBackupError("备份失败", e);
        }
    }

    private void showBackupError(String title, Exception e) {
        LOG.log(Level.WARNING, title, e);
        JOptionPane.showMessageDialog(this, title + "：\n" + e.getMessage(),
                title, JOptionPane.ERROR_MESSAGE);
    }

    private static Path ensureExtension(Path path, String extension) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(extension) ? path
                : path.resolveSibling(path.getFileName() + extension);
    }

    public void showReminderCenter() {
        ReminderCenterDialog.show(this, context);
    }

    private void openDataFolder() {
        try {
            java.awt.Desktop.getDesktop().open(AppPaths.dataDir().toFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "数据目录：" + AppPaths.dataDir(), "数据位置",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showAbout() {
        AboutDialog.show(this, context);
    }

    private String buildTooltip(MouseEvent event) {
        int viewRow = table.rowAtPoint(event.getPoint());
        int viewColumn = table.columnAtPoint(event.getPoint());
        if (viewRow < 0 || viewColumn < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        Assignment assignment = tableModel.assignmentAt(modelRow);
        int modelColumn = table.convertColumnIndexToModel(viewColumn);
        if (modelColumn == 4) {
            return "截止 " + Formatters.display(assignment.dueAt()) + "（"
                    + TimeUtils.weekday(assignment.dueAt()) + "）";
        }
        if (modelColumn == 6) {
            Urgency urgency = tableModel.urgencyAt(modelRow);
            return "紧急度 " + urgency.displayScore() + " · " + urgency.reason();
        }
        if (modelColumn == 2 && assignment.notes() != null && !assignment.notes().isBlank()) {
            return "<html><body style='width:260px'>" + escapeHtml(assignment.notes()) + "</body></html>";
        }
        if (modelColumn == 5) {
            return "权重占课程总评 " + Formatters.weight(assignment.weight());
        }
        if (modelColumn == 1) {
            return courseNames.getOrDefault(assignment.courseId(), "未分类");
        }
        return null;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    // ------------------------------------------------------------------
    // 窗口行为
    // ------------------------------------------------------------------

    private void onCloseRequested() {
        if (trayAvailable()) {
            setVisible(false);
            tray.notifyMinimized();
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this, "确定退出作业哨兵？", "退出",
                JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            exitApplication();
        }
    }

    public void showFromTray() {
        setVisible(true);
        if ((getExtendedState() & JFrame.ICONIFIED) != 0) {
            setExtendedState(getExtendedState() & ~JFrame.ICONIFIED);
        }
        toFront();
        requestFocus();
    }

    /** 以 --minimized 启动时提示用户程序已在托盘运行。 */
    public void notifyStartedInTray() {
        if (tray != null) {
            tray.notifyStarted();
        }
    }

    public void exitApplication() {
        try {
            secondTimer.stop();
            if (tray != null) {
                tray.dispose();
            }
            context.close();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "退出清理异常", e);
        }
        dispose();
        System.exit(0);
    }

    public void showToast(String message) {
        statusLeft.setText(message);
        statusLeft.setForeground(new Color(0x2563EB));
        Timer timer = new Timer(2600, e -> {
            statusLeft.setForeground(ThemeManager.mutedText());
            updateStatus(context.statisticsService().compute(courses, assignments));
        });
        timer.setRepeats(false);
        timer.start();
    }

    /** 首次启动且无数据时提示生成示例。 */
    public void firstRunHint() {
        if (context.courseService().list().isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                int choice = JOptionPane.showConfirmDialog(this,
                        "检测到还没有任何课程数据。\n是否生成一组示例数据，快速体验倒计时、提醒与成绩预估？",
                        "欢迎使用作业哨兵", JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    context.demoDataFactory().generate(ClockProvider.now());
                    refreshAll();
                }
            });
        }
    }
}
