package com.queryeer.output.table;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import com.queryeer.api.extensions.output.table.ITableContextMenuAction;

/** The table component representing a result set */
class Table extends JTable
{
    static final String POPUP_TRIGGER_LOCATION = "popupTriggerLocation";
    private final CellRenderer cellRenderer;
    private final TableColumnAdjuster adjuster = new TableColumnAdjuster(this, 10);
    private final List<Integer> adjustedWidths = new ArrayList<>();
    final JPopupMenu tablePopupMenu = new JPopupMenu();
    final AtomicBoolean columnsAdjusted = new AtomicBoolean();

    Table(List<ITableContextMenuAction> actions)
    {
        setAutoCreateRowSorter(true);
        setBorder(BorderFactory.createEmptyBorder());
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setCellSelectionEnabled(true);
        cellRenderer = new CellRenderer(actions);
        setDefaultRenderer(Object.class, cellRenderer);
        addMouseListener(cellRenderer);
        addMouseMotionListener(cellRenderer);

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
                        e.consume();
                    }
                }
            }
        });

        tablePopupMenu.add(copyWithoutHeadersAction);

        setComponentPopupMenu(tablePopupMenu);
        setTransferHandler(new TableTransferHandler());
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column)
    {
        // We want our custom cellrenderer for all columns
        return cellRenderer;
    }

    @Override
    public Point getPopupLocation(MouseEvent event)
    {
        putClientProperty(POPUP_TRIGGER_LOCATION, event != null ? event.getPoint()
                : null);
        return super.getPopupLocation(event);
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
            JTableHeader header = getTableHeader();
            if (header != null)
            {
                header.setResizingColumn(column);
                column.setWidth(adjustedWidths.get(i));
            }
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
}
