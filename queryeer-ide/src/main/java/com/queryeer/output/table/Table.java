package com.queryeer.output.table;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.WindowConstants;
import javax.swing.table.TableColumn;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.queryeer.Constants;

/** The table component representing a result set */
class Table extends JTable
{
    private final TableColumnAdjuster adjuster = new TableColumnAdjuster(this, 10);
    private final List<Integer> adjustedWidths = new ArrayList<>();
    private final Point tableClickLocation = new Point();
    private final JPopupMenu tablePopupMenu = new JPopupMenu();
    final AtomicBoolean columnsAdjusted = new AtomicBoolean();

    Table()
    {
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setCellSelectionEnabled(true);
        setDefaultRenderer(Object.class, new CellRenderer());

        getTableHeader().addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2
                        && e.getButton() == MouseEvent.BUTTON1
                        && getTableHeader().getCursor()
                                .getType() == Cursor.E_RESIZE_CURSOR)
                {
                    // Move the point a bit to left to avoid resizing wrong column
                    Point p = new Point(e.getPoint().x - 15, e.getPoint().y);
                    int col = getTableHeader().columnAtPoint(p);
                    TableColumn column = getColumnModel().getColumn(col);
                    if (column != null)
                    {
                        adjuster.adjustColumn(col, -1);
                    }
                }
            }
        });

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                tableClickLocation.setLocation(e.getPoint());
                if (e.getClickCount() == 2
                        && e.getButton() == MouseEvent.BUTTON1)
                {
                    Point point = e.getPoint();
                    int row = rowAtPoint(point);
                    int col = columnAtPoint(point);

                    if (row >= 0)
                    {
                        showValueDialog(Table.this, getValueAt(row, col), row, col, null);
                    }
                }
                else if (e.getClickCount() == 1
                        && e.getButton() == MouseEvent.BUTTON1)
                {
                    Point point = e.getPoint();
                    int row = rowAtPoint(point);
                    int col = columnAtPoint(point);
                    if (col == 0)
                    {
                        setRowSelectionInterval(row, row);
                    }
                }
            }
        });

        tablePopupMenu.add(viewAsJsonAction);
        tablePopupMenu.add(viewAsXmlAction);
        tablePopupMenu.addSeparator();
        tablePopupMenu.add(copyWithoutHeadersAction);

        setComponentPopupMenu(tablePopupMenu);
        setTransferHandler(new TableTransferHandler());
    }

    @Override
    public boolean isCellSelected(int row, int column)
    {
        // If cell 0 is selected then select whole row
        if (super.isCellSelected(row, 0))
        {
            return true;
        }
        return super.isCellSelected(row, column);
    }

    private final Action viewAsJsonAction = new AbstractAction("View as JSON", Constants.STICKY_NOTE_O)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            JTable table = (JTable) tablePopupMenu.getInvoker();
            int row = table.rowAtPoint(tableClickLocation);
            int col = table.columnAtPoint(tableClickLocation);
            Object value = table.getValueAt(row, col);
            if (value instanceof String)
            {
                try
                {
                    value = Utils.READER.readValue((String) value);
                }
                catch (IOException ee)
                {
                }
            }
            showValueDialog(table, value, row, col, SyntaxConstants.SYNTAX_STYLE_JSON);
        }
    };

    private final Action viewAsXmlAction = new AbstractAction("View as XML", Constants.FILE_CODE_O)
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            JTable table = (JTable) tablePopupMenu.getInvoker();
            int row = table.rowAtPoint(tableClickLocation);
            int col = table.columnAtPoint(tableClickLocation);
            Object value = table.getValueAt(row, col);
            if (value instanceof String)
            {
                value = Utils.formatXML((String) value);
            }
            showValueDialog(table, value, row, col, SyntaxConstants.SYNTAX_STYLE_XML);
        }
    };

    void adjustColumns()
    {
        if (!columnsAdjusted.get())
        {
            columnsAdjusted.set(true);
            adjuster.adjustColumns(250);
            int columns = getColumnCount();
            for (int i = 0; i < columns; i++)
            {
                adjustedWidths.add(getColumnModel().getColumn(i)
                        .getWidth());
            }
        }
    }

    void restoreWidths()
    {
        int size = adjustedWidths.size();
        int columnCount = getColumnModel().getColumnCount();
        for (int i = 0; i < size; i++)
        {
            if (i >= columnCount)
            {
                continue;
            }
            TableColumn column = getColumnModel().getColumn(i);
            getTableHeader().setResizingColumn(column);
            column.setWidth(adjustedWidths.get(i));
        }
    }

    private final Action copyWithoutHeadersAction = new AbstractAction("Copy without headers")
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            JTable table = (JTable) tablePopupMenu.getInvoker();
            Clipboard clipboard = Toolkit.getDefaultToolkit()
                    .getSystemClipboard();
            clipboard.setContents(TableTransferHandler.generate(table, false), null);
        }
    };

    private void showValueDialog(JTable resultTable, Object val, int row, int col, String preferredSyntax)
    {
        Object value = val;
        if (value == null)
        {
            return;
        }

        if (value.getClass()
                .isArray())
        {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++)
            {
                list.add(Array.get(value, i));
            }
            value = list;
        }

        JFrame frame = new JFrame("Json viewer - " + resultTable.getColumnName(col) + " (Row: " + (row + 1) + ")");
        frame.setIconImages(Constants.APPLICATION_ICONS);
        RSyntaxTextArea rta = new RSyntaxTextArea();
        // CSOFF
        rta.setColumns(80);
        rta.setRows(40);
        // CSON
        if (value instanceof Collection
                || value instanceof Map)
        {
            // Always use json for map/collection types
            rta.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
            rta.setCodeFoldingEnabled(true);
            rta.setBracketMatchingEnabled(true);
            rta.setText(Model.getPrettyJson(value));
        }
        else
        {
            rta.setSyntaxEditingStyle(preferredSyntax);
            rta.setText(String.valueOf(value));
        }
        rta.setCaretPosition(0);
        rta.setEditable(false);
        RTextScrollPane sp = new RTextScrollPane(rta);
        frame.getContentPane()
                .add(sp);
        frame.setSize(Constants.DEFAULT_DIALOG_SIZE);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }
}
