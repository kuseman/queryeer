package com.queryeer.api.extensions.output.table;

import com.queryeer.api.extensions.output.IOutputComponent;

/** Definition of the built in table output component */
public interface ITableOutputComponent extends IOutputComponent
{
    /** Returns the value in the cell the was last clicked. Ie. the cell that a context menu was opened at. etc */
    ClickedCell getLastClickedCell();

    /** Definition of a clicked cell */
    interface ClickedCell
    {
        /** Get the column header of the cell */
        String getColumnHeader();

        /** Get value of the cell */
        Object getValue();
    }
}
