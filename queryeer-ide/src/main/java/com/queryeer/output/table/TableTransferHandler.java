package com.queryeer.output.table;

import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

import com.queryeer.api.extensions.output.table.TableTransferable;

/** Transfer handler for query table */
class TableTransferHandler extends TransferHandler
{
    /**
     * Create a Transferable to use as the source for a data transfer.
     *
     * @param c The component holding the data to be transfered. This argument is provided to enable sharing of TransferHandlers by multiple components.
     * @return The representation of the data to be transfered.
     */
    @Override
    protected Transferable createTransferable(JComponent c)
    {
        if (c instanceof JTable)
        {
            // Default action includes headers
            return generate((JTable) c, true);
        }

        return null;
    }

    @Override
    public int getSourceActions(JComponent c)
    {
        return COPY;
    }

    /** Create a data transferable from provided jtable */
    // CSOFF
    static Transferable generate(JTable table, boolean includeHeaders)
    // CSON
    {
        int[] rows;
        int[] cols;

        if (!table.getRowSelectionAllowed()
                && !table.getColumnSelectionAllowed())
        {
            return null;
        }

        if (!table.getRowSelectionAllowed())
        {
            int rowCount = table.getRowCount();

            rows = new int[rowCount];
            for (int counter = 0; counter < rowCount; counter++)
            {
                rows[counter] = counter;
            }
        }
        else
        {
            rows = table.getSelectedRows();
        }

        if (!table.getColumnSelectionAllowed())
        {
            int colCount = table.getColumnCount();

            cols = new int[colCount];
            for (int counter = 0; counter < colCount; counter++)
            {
                cols[counter] = counter;
            }
        }
        else
        {
            cols = table.getSelectedColumns();
        }

        if (rows == null
                || cols == null
                || rows.length == 0
                || cols.length == 0)
        {
            return null;
        }

        String[] headerNames = new String[cols.length];
        List<Object[]> rowsValues = new ArrayList<>(rows.length);

        for (int col = 0; col < cols.length; col++)
        {
            String columnName = table.getColumnName(cols[col]);
            if (!includeHeaders)
            {
                columnName = "";
            }
            headerNames[col] = columnName;
        }

        for (int row = 0; row < rows.length; row++)
        {
            Object[] values = new Object[cols.length];
            rowsValues.add(values);
            for (int col = 0; col < cols.length; col++)
            {
                Object obj = table.getValueAt(rows[row], cols[col]);
                values[col] = obj;
            }
        }

        return new TableTransferable(headerNames, rowsValues);
    }
}
