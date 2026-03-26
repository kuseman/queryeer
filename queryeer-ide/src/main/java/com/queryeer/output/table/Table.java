package com.queryeer.output.table;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import com.queryeer.UiUtils;
import com.queryeer.api.extensions.output.table.ITableContextMenuAction;

/** The table component representing a result set */
class Table extends JTable
{
    static final String POPUP_TRIGGER_LOCATION = "popupTriggerLocation";
    final CellRenderer cellRenderer;
    private final TableColumnAdjuster adjuster = new TableColumnAdjuster(this, 10);
    private final List<Integer> adjustedWidths = new ArrayList<>();
    final JPopupMenu tablePopupMenu = new JPopupMenu();
    final AtomicBoolean columnsAdjusted = new AtomicBoolean();

    /**
     * Tracks individually CTRL-clicked cells to avoid JTable's cross-product selection. JTable stores row/column selections in separate models, so CTRL-clicking (r1,c1) then (r2,c2) would select all
     * 4 intersecting cells. This set lets us track exact cells and override isCellSelected/getSelectedRows/getSelectedColumns accordingly.
     */
    private final Set<Point> ctrlSelectedCells = new LinkedHashSet<>();

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
        adaptToLookAndFeel();
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
    public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend)
    {
        if (ctrlSelectedCells == null)
        {
            super.changeSelection(rowIndex, columnIndex, toggle, extend);
            return;
        }
        if (toggle
                && !extend)
        {
            // CTRL-click: toggle individual cell to avoid JTable's cross-product selection.
            // Call super with toggle=false to keep JTable's anchor/focus in sync.
            if (ctrlSelectedCells.isEmpty())
            {
                // First CTRL-click while no individual cells are tracked yet: seed the set from
                // the underlying model so any existing plain/shift selection stays visible.
                for (int r : super.getSelectedRows())
                {
                    for (int c : super.getSelectedColumns())
                    {
                        ctrlSelectedCells.add(new Point(r, c));
                    }
                }
            }
            Point cell = new Point(rowIndex, columnIndex);
            if (ctrlSelectedCells.contains(cell))
            {
                ctrlSelectedCells.remove(cell);
            }
            else
            {
                ctrlSelectedCells.add(cell);
            }
            super.changeSelection(rowIndex, columnIndex, false, false);
            return;
        }
        if (toggle
                && extend)
        {
            // CTRL+SHIFT+click: extend from the current anchor to the clicked cell, adding the
            // rectangle of cells to ctrlSelectedCells while keeping previously selected cells.
            int anchorRow = getSelectionModel().getAnchorSelectionIndex();
            int anchorCol = getColumnModel().getSelectionModel()
                    .getAnchorSelectionIndex();
            if (anchorRow >= 0
                    && anchorCol >= 0)
            {
                int r0 = Math.min(anchorRow, rowIndex);
                int r1 = Math.max(anchorRow, rowIndex);
                int c0 = Math.min(anchorCol, columnIndex);
                int c1 = Math.max(anchorCol, columnIndex);
                for (int r = r0; r <= r1; r++)
                {
                    for (int c = c0; c <= c1; c++)
                    {
                        ctrlSelectedCells.add(new Point(r, c));
                    }
                }
            }
            else
            {
                ctrlSelectedCells.add(new Point(rowIndex, columnIndex));
            }
            // Keep JTable's anchor at its current position; only move the lead.
            super.changeSelection(rowIndex, columnIndex, toggle, extend);
            repaint();
            return;
        }
        if (!extend)
        {
            // Plain click or CTRL+SHIFT: clear the individual cell set.
            // Drag/shift-only (extend=true, toggle=false) does NOT clear: a tiny mouse movement
            // between press and release fires mouseDragged (extend=true) and must not wipe out
            // the CTRL selection. We still call super so JTable's anchor stays valid.
            boolean hadCtrlCells = !ctrlSelectedCells.isEmpty();
            ctrlSelectedCells.clear();
            if (hadCtrlCells)
            {
                // JTable's selection-change events only repaint rows/cols that changed in the
                // underlying model (which only tracked the last CTRL+clicked cell). Cells that
                // were in ctrlSelectedCells but not in the underlying model won't get a repaint
                // event and would remain visually highlighted. Force a full repaint to fix this.
                repaint();
            }
        }
        super.changeSelection(rowIndex, columnIndex, toggle, extend);
    }

    @Override
    public boolean isCellSelected(int row, int column)
    {
        if (!ctrlSelectedCells.isEmpty())
        {
            // If cell 0 is selected then select whole row (preserve existing row-select behavior)
            if (ctrlSelectedCells.contains(new Point(row, 0)))
            {
                return true;
            }
            return ctrlSelectedCells.contains(new Point(row, column));
        }
        // If cell 0 is selected then select whole row
        if (super.isCellSelected(row, 0))
        {
            return true;
        }
        return super.isCellSelected(row, column);
    }

    @Override
    public int[] getSelectedRows()
    {
        if (!ctrlSelectedCells.isEmpty())
        {
            return ctrlSelectedCells.stream()
                    .mapToInt(p -> p.x)
                    .distinct()
                    .sorted()
                    .toArray();
        }
        return super.getSelectedRows();
    }

    @Override
    public int[] getSelectedColumns()
    {
        if (!ctrlSelectedCells.isEmpty())
        {
            return ctrlSelectedCells.stream()
                    .mapToInt(p -> p.y)
                    .distinct()
                    .sorted()
                    .toArray();
        }
        return super.getSelectedColumns();
    }

    @Override
    public void setRowSelectionInterval(int index0, int index1)
    {
        if (ctrlSelectedCells != null
                && !ctrlSelectedCells.isEmpty())
        {
            ctrlSelectedCells.clear();
            repaint();
        }
        super.setRowSelectionInterval(index0, index1);
    }

    @Override
    public void setColumnSelectionInterval(int index0, int index1)
    {
        if (ctrlSelectedCells != null
                && !ctrlSelectedCells.isEmpty())
        {
            ctrlSelectedCells.clear();
            repaint();
        }
        super.setColumnSelectionInterval(index0, index1);
    }

    @Override
    public void clearSelection()
    {
        // ctrlSelectedCells may be null when called from JTable's own constructor
        // (via setModel → tableChanged → clearSelectionAndLeadAnchor) before our field initializes
        if (ctrlSelectedCells != null
                && !ctrlSelectedCells.isEmpty())
        {
            ctrlSelectedCells.clear();
            repaint();
        }
        super.clearSelection();
    }

    void adaptToLookAndFeel()
    {
        cellRenderer.setDarkMode(UiUtils.isDarkLookAndFeel());
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
