package com.queryeer.api.extensions.output.table;

import java.util.List;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;

/** Definition of the built in table output component */
public interface ITableOutputComponent extends IOutputComponent
{
    /**
     * Returns all selected cells in the active table. Returns one entry per actually-selected cell, correctly handling sparse CTRL+click, contiguous block, and single-cell selections. Returns an
     * empty list if no selection exists.
     */
    List<SelectedCell> getSelectedCells();

    /** Returns all tables from this component. */
    List<Table> getTables();

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

    /** A table inside the output component. */
    interface Table
    {
        /** Returns the row count from this table. */
        int getRowCount();

        /** Returns the column names from this table. */
        List<String> getColumns();

        /** Returns the column types from this table. */
        List<Class<?>> getTypes();

        /** Return value at row/col. */
        Object getValueAt(int row, int column);
    }
}
