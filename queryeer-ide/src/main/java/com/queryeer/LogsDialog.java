package com.queryeer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

import com.queryeer.api.component.DialogUtils;

/** Logs dialog */
class LogsDialog extends DialogUtils.AFrame
{
    static JTextPane textPane;
    private DefaultTableModel loggersModel;

    LogsDialog(JFrame parent)
    {
        super("Logs");
        if (textPane != null)
        {
            throw new IllegalArgumentException("Logs dialog instantiated multiple times");
        }

        initDialog();

        LogManager.getRootLogger()
                .addAppender(new LogsAppender());
    }

    private void initDialog()
    {
        setTitle("Logs");
        getContentPane().setLayout(new BorderLayout());

        textPane = new JTextPane();
        textPane.setEditable(false);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clear = new JButton("Clear");
        clear.setHorizontalAlignment(SwingConstants.LEFT);
        clear.addActionListener(l -> textPane.setText(""));
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

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Logs", new JScrollPane(textPane));

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

    static class LogsAppender extends AppenderSkeleton
    {
        // CSOFF
        private static SimpleAttributeSet ERROR_ATT, WARN_ATT, INFO_ATT, DEBUG_ATT, TRACE_ATT, RESTO_ATT;
        // CSON

        static
        {
            // ERROR
            ERROR_ATT = new SimpleAttributeSet();
            ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.TRUE);
            ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
            ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 0, 0));

            // WARN
            WARN_ATT = new SimpleAttributeSet();
            WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
            WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
            WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 76, 0));

            // INFO
            INFO_ATT = new SimpleAttributeSet();
            INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
            INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
            INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(0, 0, 153));

            // DEBUG
            DEBUG_ATT = new SimpleAttributeSet();
            DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
            DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
            DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(64, 64, 64));

            // TRACE
            TRACE_ATT = new SimpleAttributeSet();
            TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
            TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
            TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 0, 76));

            // RESTO
            RESTO_ATT = new SimpleAttributeSet();
            RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
            RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
            RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(0, 0, 0));
        }

        LogsAppender()
        {
            setLayout(new EnhancedPatternLayout("%d{ISO8601} [%t] %p %c %x - %m%n%throwable"));
        }

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
            String formattedMsg = layout.format(event);

            SwingUtilities.invokeLater(() ->
            {
                JTextPane textPane = LogsDialog.textPane;

                try
                {
                    int limite = 1000;
                    int apaga = 200;
                    if (textPane.getDocument()
                            .getDefaultRootElement()
                            .getElementCount() > limite)
                    {
                        int end = getLineEndOffset(textPane, apaga);
                        replaceRange(textPane, null, 0, end);
                    }

                    if (event.getLevel() == Level.ERROR)
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, ERROR_ATT);
                    else if (event.getLevel() == Level.WARN)
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, WARN_ATT);
                    else if (event.getLevel() == Level.INFO)
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, INFO_ATT);
                    else if (event.getLevel() == Level.DEBUG)
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, DEBUG_ATT);
                    else if (event.getLevel() == Level.TRACE)
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, TRACE_ATT);
                    else
                        textPane.getDocument()
                                .insertString(textPane.getDocument()
                                        .getLength(), formattedMsg, RESTO_ATT);
                }
                catch (BadLocationException e)
                {
                }

                textPane.setCaretPosition(textPane.getDocument()
                        .getLength());
            });
        }

        private int getLineCount(JTextPane textPane)
        {
            return textPane.getDocument()
                    .getDefaultRootElement()
                    .getElementCount();
        }

        private int getLineEndOffset(JTextPane textPane, int line) throws BadLocationException
        {
            int lineCount = getLineCount(textPane);
            if (line < 0)
            {
                throw new BadLocationException("Negative line", -1);
            }
            else if (line >= lineCount)
            {
                throw new BadLocationException("No such line", textPane.getDocument()
                        .getLength() + 1);
            }
            else
            {
                Element map = textPane.getDocument()
                        .getDefaultRootElement();
                Element lineElem = map.getElement(line);
                int endOffset = lineElem.getEndOffset();
                return ((line == lineCount - 1) ? (endOffset - 1)
                        : endOffset);
            }
        }

        private void replaceRange(JTextPane textPane, String str, int start, int end) throws IllegalArgumentException
        {
            if (end < start)
            {
                throw new IllegalArgumentException("end before start");
            }
            Document doc = textPane.getDocument();
            if (doc != null)
            {
                try
                {
                    if (doc instanceof AbstractDocument)
                    {
                        ((AbstractDocument) doc).replace(start, end - start, str, null);
                    }
                    else
                    {
                        doc.remove(start, end - start);
                        doc.insertString(start, str, null);
                    }
                }
                catch (BadLocationException e)
                {
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
        }
    }
}
