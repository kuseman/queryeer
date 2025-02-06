package com.queryeer;

import static java.util.stream.Collectors.joining;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.component.IDialogFactory;

/** Logs dialog */
class LogsDialog extends DialogUtils.AFrame
{
    static DefaultTableModel logsModel;
    private DefaultTableModel loggersModel;
    private IDialogFactory dialogFactory;

    LogsDialog(JFrame parent, IDialogFactory dialogFactory)
    {
        super("Logs");
        if (logsModel != null)
        {
            throw new IllegalArgumentException("Logs dialog instantiated multiple times");
        }
        this.dialogFactory = dialogFactory;

        initDialog();

        LogManager.getRootLogger()
                .addAppender(new LogsAppender());
    }

    private void initDialog()
    {
        setTitle("Logs");
        getContentPane().setLayout(new BorderLayout());

        JCheckBox scrollLock = new JCheckBox("Scroll Lock");
        JTable logsTable = new JTable();
        logsTable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e)
                        && e.getClickCount() == 2)
                {
                    int row = logsTable.rowAtPoint(e.getPoint());
                    int col = logsTable.columnAtPoint(e.getPoint());
                    logsTable.setRowSelectionInterval(row, row);
                    String header = logsTable.getModel()
                            .getColumnName(col);
                    dialogFactory.showValueDialog(header, logsTable.getValueAt(row, col), IDialogFactory.Format.UNKOWN);
                }
            }
        });
        logsModel = new DefaultTableModel()
        {
            @Override
            public boolean isCellEditable(int row, int column)
            {
                return false;
            }

            @Override
            public void insertRow(int row, Object[] rowData)
            {
                super.insertRow(row, rowData);
                if (!scrollLock.isSelected())
                {
                    logsTable.scrollRectToVisible(logsTable.getCellRect(0, 0, true));
                }
            }
        };
        logsModel.addColumn("Time");
        logsModel.addColumn("Thread");
        logsModel.addColumn("Level");
        logsModel.addColumn("Logger");
        logsModel.addColumn("Message");
        logsModel.addColumn("Stacktrace");

        logsTable.setModel(logsModel);
        logsTable.getColumnModel()
                .getColumn(2)
                .setCellRenderer(new DefaultTableCellRenderer()
                {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
                    {
                        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                        Level level = (Level) value;
                        LevelStyle style = LEVEL_STYLES.getOrDefault(level, DEFAULT);
                        Font font = getFont();
                        setForeground(style.color);
                        if (style.bold)
                        {
                            font = font.deriveFont(Font.BOLD);
                        }
                        if (style.italic)
                        {
                            font = font.deriveFont(Font.ITALIC);
                        }
                        setFont(font);
                        setText(level.toString());
                        return this;
                    }
                });

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clear = new JButton("Clear");
        clear.setHorizontalAlignment(SwingConstants.LEFT);
        clear.addActionListener(l -> logsModel.setRowCount(0));
        topPanel.add(clear);

        JComboBox<Level> logLevel = new JComboBox<>();
        logLevel.addItem(Level.OFF);
        logLevel.addItem(Level.FATAL);
        logLevel.addItem(Level.ERROR);
        logLevel.addItem(Level.WARN);
        logLevel.addItem(Level.INFO);
        logLevel.addItem(Level.DEBUG);
        logLevel.addItem(Level.TRACE);
        logLevel.addItem(Level.ALL);

        logLevel.setSelectedItem(LogManager.getRootLogger()
                .getLevel());
        logLevel.addActionListener(l ->
        {
            Level level = (Level) logLevel.getSelectedItem();
            if (level != null)
            {
                LogManager.getRootLogger()
                        .setLevel(level);
            }
        });
        topPanel.add(new JLabel("Root Log Level:"));
        topPanel.add(logLevel);
        topPanel.add(scrollLock);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Logs", new JScrollPane(logsTable));

        JPanel loggersPanel = new JPanel(new BorderLayout());
        loggersPanel.add(new JScrollPane(createLoggersTable()), BorderLayout.CENTER);
        tabbedPane.add("Loggers", loggersPanel);

        getContentPane().add(topPanel, BorderLayout.NORTH);
        getContentPane().add(tabbedPane, BorderLayout.CENTER);

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setPreferredSize(Constants.DEFAULT_DIALOG_SIZE);
        pack();
        setLocationRelativeTo(null);
        pack();
    }

    private JTable createLoggersTable()
    {
        loggersModel = new DefaultTableModel()
        {
            @Override
            public boolean isCellEditable(int row, int column)
            {
                return column == 1;
            }

            @Override
            public java.lang.Class<?> getColumnClass(int columnIndex)
            {
                if (columnIndex == 0)
                {
                    return Logger.class;
                }

                return Level.class;
            }

            @Override
            public void setValueAt(Object value, int row, int column)
            {
                Logger logger = (Logger) getValueAt(row, 0);
                logger.setLevel((Level) value);
                super.setValueAt(value, row, column);
            }
        };
        loggersModel.addColumn("Logger");
        loggersModel.addColumn("Level");

        JTable loggersTable = new JTable();
        loggersTable.putClientProperty("terminateEditOnFocusLost", true);
        loggersTable.setModel(loggersModel);
        loggersTable.setRowSorter(new TableRowSorter<TableModel>(loggersModel)
        {
            @Override
            public Comparator<?> getComparator(int column)
            {
                if (column == 0)
                {
                    return (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(((Logger) a).getName(), ((Logger) b).getName());
                }
                return super.getComparator(column);
            }

            @Override
            protected boolean useToString(int column)
            {
                return column == 1;
            }
        });

        loggersTable.getColumnModel()
                .getColumn(0)
                .setCellRenderer(new DefaultTableCellRenderer()
                {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
                    {
                        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                        Logger logger = (Logger) value;
                        setText(logger.getName());
                        return this;
                    }
                });
        loggersTable.getColumnModel()
                .getColumn(1)
                .setCellRenderer(new DefaultTableCellRenderer()
                {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
                    {
                        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                        Level level = (Level) value;
                        if (level != null)
                        {
                            setText(level.toString());
                        }
                        return this;
                    }
                });

        JComboBox<Level> logLevel = new JComboBox<>();
        logLevel.addItem(Level.OFF);
        logLevel.addItem(Level.FATAL);
        logLevel.addItem(Level.ERROR);
        logLevel.addItem(Level.WARN);
        logLevel.addItem(Level.INFO);
        logLevel.addItem(Level.DEBUG);
        logLevel.addItem(Level.TRACE);
        logLevel.addItem(Level.ALL);

        loggersTable.getColumnModel()
                .getColumn(1)
                .setCellEditor(new DefaultCellEditor(logLevel));

        return loggersTable;
    }

    @Override
    public void setVisible(boolean b)
    {
        super.setVisible(b);
        if (b)
        {
            refreshLoggersModel();
        }
    }

    private void refreshLoggersModel()
    {
        List<Logger> loggers = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Enumeration<Logger> e = LogManager.getCurrentLoggers();
        while (e.hasMoreElements())
        {
            Logger logger = e.nextElement();
            loggers.add(logger);
        }

        loggers.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));

        loggersModel.setRowCount(0);
        for (Logger logger : loggers)
        {
            loggersModel.addRow(new Object[] { logger, logger.getLevel() });
        }
    }

    //@formatter:off
    private static final LevelStyle DEFAULT = new LevelStyle(false, true, new Color(0, 0, 0));
    private static final Map<Level, LevelStyle> LEVEL_STYLES = Map.of(
            Level.ERROR, new LevelStyle(true, false, new Color(153, 0, 0)),
            Level.WARN, new LevelStyle(false, false, new Color(153, 76, 0)),
            Level.INFO, new LevelStyle(false, false, new Color(0, 0, 153)),
            Level.DEBUG, new LevelStyle(false, true, new Color(64, 64, 64)),
            Level.TRACE, new LevelStyle(false, true, new Color(153, 0, 76))
            );
    //@formatter:on

    record LevelStyle(boolean bold, boolean italic, Color color)
    {
    }

    static class LogsAppender extends AppenderSkeleton
    {
        @Override
        public void close()
        {
        }

        @Override
        public boolean requiresLayout()
        {
            return true;
        }

        @Override
        protected void append(LoggingEvent event)
        {
            SwingUtilities.invokeLater(() ->
            {
                //@formatter:off
                Object[] row = new Object[] {
                        DateTimeFormatter.ISO_DATE_TIME.format(Instant.ofEpochMilli(event.getTimeStamp()).atZone(ZoneId.systemDefault())),
                        event.getThreadName(),
                        event.getLevel(),
                        event.getLoggerName(),
                        event.getMessage(),
                        event.getThrowableStrRep() != null
                            ? Arrays.stream(event.getThrowableStrRep()).collect(joining(System.lineSeparator()))
                            : null
                        };
                //@formatter:on

                // We want a log that has the recent logs on top
                LogsDialog.logsModel.insertRow(0, row);
            });
        }
    }
}
