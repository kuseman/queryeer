package com.queryeer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.api.component.AnimatedIcon;
import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.component.IDialogFactory;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.service.IEventBus;
import com.queryeer.domain.Task;
import com.queryeer.event.TaskCompletedEvent;
import com.queryeer.event.TaskStartedEvent;

/** Tasks dialog */
class TasksDialog extends DialogUtils.AFrame
{
    private static final Icon CHECK_CIRCLE = FontIcon.of(FontAwesome.CHECK_CIRCLE);
    private static final Icon EXCLAMATION_CIRCLE = FontIcon.of(FontAwesome.EXCLAMATION_CIRCLE);
    private static final Icon SPINNER = AnimatedIcon.createSmallSpinner();

    private JTable tasksTable;
    private TasksTableModel tasksTableModel;
    private final Timer timer;
    private final Consumer<Boolean> tasksRunningConsumer;
    private final IDialogFactory dialogFactory;

    TasksDialog(JFrame parent, IEventBus eventBus, IDialogFactory dialogFactory, Consumer<Boolean> tasksRunningConsumer)
    {
        super("Tasks");
        this.dialogFactory = dialogFactory;
        this.tasksRunningConsumer = tasksRunningConsumer;
        initDialog();
        eventBus.register(this);
        // NOTE! after init dialog
        this.timer = new Timer(150, timerListener);
        this.timer.stop();
    }

    private void initDialog()
    {
        setTitle("Tasks");
        getContentPane().setLayout(new BorderLayout());

        tasksTableModel = new TasksTableModel();
        tasksTable = new JTable(tasksTableModel);

        tasksTable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2)
                {
                    Point point = e.getPoint();
                    int row = tasksTable.rowAtPoint(point);
                    int col = tasksTable.columnAtPoint(point);

                    // Show value dialog of status on double click
                    if (col == TasksTableModel.STATUS_COLUMN_INDEX)
                    {
                        TaskRow taskRow = tasksTableModel.tasks.get(row);
                        dialogFactory.showValueDialog("Task status - " + taskRow.task.getName(), tasksTable.getValueAt(row, col), IDialogFactory.Format.UNKOWN);
                    }
                }
            }
        });

        // Icon
        tasksTable.getColumnModel()
                .getColumn(0)
                .setMaxWidth(30);

        // Actions
        ActionsCellEditor cellEditor = new ActionsCellEditor();
        tasksTable.getColumnModel()
                .getColumn(6)
                .setCellRenderer(cellEditor);
        tasksTable.getColumnModel()
                .getColumn(6)
                .setCellEditor(cellEditor);

        getContentPane().add(new JScrollPane(tasksTable), BorderLayout.CENTER);

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setPreferredSize(Constants.DEFAULT_DIALOG_SIZE);
        pack();
        setLocationRelativeTo(null);
        pack();
    }

    private static class ActionsCellEditor extends AbstractCellEditor implements TableCellEditor, TableCellRenderer
    {
        private JToolBar toolBar;
        private JToolBar toolBarRender;

        ActionsCellEditor()
        {
            this.toolBar = new JToolBar();
            this.toolBar.setOpaque(true);
            this.toolBar.setFloatable(false);

            this.toolBarRender = new JToolBar();
            this.toolBarRender.setOpaque(true);
            this.toolBarRender.setFloatable(false);
        }

        @Override
        public Object getCellEditorValue()
        {
            return null;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
        {
            @SuppressWarnings("unchecked")
            List<Action> actions = (List<Action>) value;
            toolBar.removeAll();
            for (Action action : actions)
            {
                toolBar.add(action)
                        .setOpaque(true);
            }
            if (isSelected)
            {
                toolBar.setBackground(table.getSelectionBackground());
            }
            else
            {
                toolBar.setBackground(table.getBackground());
            }

            return toolBar;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
        {
            @SuppressWarnings("unchecked")
            List<Action> actions = (List<Action>) value;
            toolBarRender.removeAll();
            for (Action action : actions)
            {
                toolBarRender.add(action)
                        .setOpaque(true);
            }

            if (isSelected
                    || hasFocus)
            {
                toolBarRender.setBackground(table.getSelectionBackground());
            }
            else
            {
                toolBarRender.setBackground(table.getBackground());
            }

            return toolBarRender;
        }

    }

    private ActionListener timerListener = new ActionListener()
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            // Stop the timer when there are no tasks left in running state
            if (tasksTableModel.tasks.stream()
                    .allMatch(t -> t.status != TaskRow.Status.RUNNING))
            {
                tasksRunningConsumer.accept(false);
                timer.stop();
            }

            tasksTableModel.fireTableDataChanged();
        }
    };

    @Subscribe
    private void taskStarted(TaskStartedEvent event)
    {
        synchronized (this)
        {
            TaskRow taskRow = tasksTableModel.taskByKey.get(event.getTask()
                    .getKey());

            if (taskRow != null)
            {
                taskRow.setTask(event.getTask());

                if (!timer.isRunning())
                {
                    tasksRunningConsumer.accept(true);
                    timer.start();
                }
                return;
            }

            taskRow = new TaskRow(event.getTask());
            tasksTableModel.tasks.add(taskRow);
            tasksTableModel.taskByKey.put(event.getTask()
                    .getKey(), taskRow);
            SwingUtilities.invokeLater(() -> tasksTableModel.fireTableDataChanged());

            if (!timer.isRunning())
            {
                tasksRunningConsumer.accept(true);
                timer.start();
            }
        }
    }

    @Subscribe
    private void taskCompleted(TaskCompletedEvent event)
    {
        TaskRow taskRow = tasksTableModel.taskByKey.get(event.getTaskKey());
        if (taskRow != null)
        {
            taskRow.complete(event.getException());
            SwingUtilities.invokeLater(() -> tasksTableModel.fireTableDataChanged());
        }
    }

    private static class TasksTableModel extends AbstractTableModel
    {
        private static final int STATUS_COLUMN_INDEX = 3;
        private static final int ACTIONS_COLUMN_INDEX = 6;
        private static final String[] COLUMNS = new String[] { "", "Task", "Description", "Status", "Ellapsed", "Completed", "Actions" };

        private final Map<Object, TaskRow> taskByKey = new HashMap<>();
        private final List<TaskRow> tasks = new ArrayList<>();

        @Override
        public int getRowCount()
        {
            return tasks.size();
        }

        @Override
        public int getColumnCount()
        {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column)
        {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex)
        {
            switch (columnIndex)
            {
                case 0:
                    return ImageIcon.class;
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                    return String.class;
                case ACTIONS_COLUMN_INDEX:
                    return List.class;
                default:
                    throw new IllegalArgumentException("Illegal column " + columnIndex);
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            return columnIndex == ACTIONS_COLUMN_INDEX;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            TaskRow task = tasks.get(rowIndex);

            switch (columnIndex)
            {
                case 0:
                    return task.getIcon();
                case 1:
                    return task.task.getName();
                case 2:
                    return task.task.getDescription();
                case 3:
                    if (task.status == TaskRow.Status.RUNNING)
                    {
                        return "";
                    }
                    return task.statusText;
                case 4:
                    long duration;
                    if (task.status == TaskRow.Status.RUNNING)
                    {
                        duration = ChronoUnit.MILLIS.between(task.task.getStartTime(), LocalDateTime.now());
                    }
                    else if (task.completed != null)
                    {
                        duration = ChronoUnit.MILLIS.between(task.task.getStartTime(), task.completed);
                    }
                    else
                    {
                        return "";
                    }

                    return DurationFormatUtils.formatDurationHMS(duration);
                case 5:
                    if (task.status == TaskRow.Status.RUNNING
                            || task.completed == null)
                    {
                        return "";
                    }
                    return task.completed;
                case ACTIONS_COLUMN_INDEX:
                    return task.task.getActions();
                default:
                    throw new IllegalArgumentException("Illegal column " + columnIndex);
            }
        }

    }

    private static class TaskRow
    {
        private Task task;
        private Status status = Status.RUNNING;
        private String statusText;
        private LocalDateTime completed;

        TaskRow(Task task)
        {
            this.task = task;
        }

        void setTask(Task task)
        {
            this.task = task;
            status = Status.RUNNING;
        }

        void complete(Throwable t)
        {
            completed = LocalDateTime.now();
            status = Status.COMPLETED;
            statusText = "Success";
            if (t != null)
            {
                statusText = ExceptionUtils.getStackTrace(t);
                status = Status.FAILED;
            }
        }

        Icon getIcon()
        {
            switch (status)
            {
                case RUNNING:
                    return SPINNER;
                case FAILED:
                    return EXCLAMATION_CIRCLE;
                default:
                    return CHECK_CIRCLE;
            }
        }

        enum Status
        {
            RUNNING,
            COMPLETED,
            FAILED
        }
    }
}