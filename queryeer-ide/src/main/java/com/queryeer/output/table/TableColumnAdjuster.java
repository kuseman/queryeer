package com.queryeer.output.table;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/** Table column adjuster */
class TableColumnAdjuster
{
    private static final int DEFAULT_SPACING = 6;
    private final JTable table;
    private final int spacing;

    TableColumnAdjuster(JTable table)
    {
        this(table, DEFAULT_SPACING);
    }

    TableColumnAdjuster(JTable table, int spacing)
    {
        this.table = table;
        this.spacing = spacing;
    }

    void adjustColumns(int maxWidth)
    {
        TableColumnModel tcm = table.getColumnModel();

        int columnCount = tcm.getColumnCount();
        for (int i = 0; i < columnCount; i++)
        {
            adjustColumn(i, maxWidth);
        }
    }

    /*
     * Adjust the width of the specified column in the table
     */
    void adjustColumn(final int column, int maxWidth)
    {
        TableColumn tableColumn = table.getColumnModel()
                .getColumn(column);
        if (!tableColumn.getResizable())
        {
            return;
        }

        int columnHeaderWidth = getColumnHeaderWidth(column);
        int columnDataWidth = getColumnDataWidth(column);
        int preferredWidth = Math.max(columnHeaderWidth, columnDataWidth);

        updateTableColumn(column, preferredWidth, maxWidth);
    }

    /*
     * Calculated the width based on the column name
     */
    private int getColumnHeaderWidth(int column)
    {
        TableColumn tableColumn = table.getColumnModel()
                .getColumn(column);
        Object value = tableColumn.getHeaderValue();
        TableCellRenderer renderer = tableColumn.getHeaderRenderer();

        if (renderer == null)
        {
            renderer = table.getTableHeader()
                    .getDefaultRenderer();
        }

        Component c = renderer.getTableCellRendererComponent(table, value, false, false, -1, column);
        return c.getPreferredSize().width;
    }

    private int getColumnDataWidth(int column)
    {
        int preferredWidth = 0;
        int maxWidth = table.getColumnModel()
                .getColumn(column)
                .getMaxWidth();
        int rowCount = table.getRowCount();
        for (int row = 0; row < rowCount; row++)
        {
            preferredWidth = Math.max(preferredWidth, getCellDataWidth(row, column));
            if (preferredWidth >= maxWidth)
            {
                break;
            }
        }

        return preferredWidth;
    }

    private int getCellDataWidth(int row, int column)
    {
        if (column >= table.getColumnCount())
        {
            return 0;
        }
        TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
        Component c = table.prepareRenderer(cellRenderer, row, column);
        int width = c.getPreferredSize().width + table.getIntercellSpacing().width;

        return width;
    }

    private void updateTableColumn(int column, int w, int maxWidth)
    {
        int width = w;
        TableColumn tableColumn = table.getColumnModel()
                .getColumn(column);
        if (!tableColumn.getResizable())
        {
            return;
        }

        width += spacing;
        table.getTableHeader()
                .setResizingColumn(tableColumn);
        tableColumn.setWidth(maxWidth != -1 ? Math.min(width, maxWidth)
                : width);
    }
}
