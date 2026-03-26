package com.queryeer.api.extensions.output.table;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;

/** Definition of the built in table output component */
public interface ITableOutputComponent extends IOutputComponent
{
    /**
     * Returns all selected cells in the active table. Returns one entry per actually-selected cell, correctly handling sparse CTRL+click, contiguous block, and single-cell selections. Returns an
     * empty list if no selection exists.
     */
    java.util.List<SelectedCell> getSelectedCells();

    /** Return this query files associated query file. */
    IQueryFile getQueryFile();

    /** Select row in a table in this component. */
    void selectRow(int tableIndex, int row);

    /** A single selected cell with access to its full row's data. */
    interface SelectedCell
    {
        /** Row index of this cell (view index) */
        int getRowIndex();

        /** Column index of this cell */
        int getColumnIndex();

        /** Column header of this cell */
        String getColumnHeader();

        /** Value at this cell */
        Object getCellValue();

        /** Total number of columns in the table */
        int getColumnCount();

        /** Value at any column in this row */
        Object getRowValue(int columnIndex);

        /** Column header at any column index */
        String getRowHeader(int columnIndex);
    }
}
