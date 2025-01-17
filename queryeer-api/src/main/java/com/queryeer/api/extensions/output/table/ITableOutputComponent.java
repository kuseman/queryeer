package com.queryeer.api.extensions.output.table;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;

/** Definition of the built in table output component */
public interface ITableOutputComponent extends IOutputComponent
{
    /**
     * Returns the selected row if the table component. Or null if no row is selected. This can be used to get the value of a clicked cell in actions etc.
     */
    SelectedRow getSelectedRow();

    /** Return this query files associated query file. */
    IQueryFile getQueryFile();

    /** Select row in a table in this component. */
    void selectRow(int tableIndex, int row);

    /** Definition of a selected row. */
    interface SelectedRow
    {
        /** Get the column header of the selected cell */
        String getCellHeader();

        /** Get value of the selected cell */
        Object getCellValue();

        /** Return column count of the selected row. */
        int getColumnCount();

        /** Get value at provided column index. */
        Object getValue(int columnIndex);

        /** Get column header at provided column index. */
        String getHeader(int columnIndex);
    }
}
