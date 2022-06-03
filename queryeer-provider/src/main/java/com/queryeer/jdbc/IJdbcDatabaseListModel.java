package com.queryeer.jdbc;

import javax.swing.ListModel;

/** Definition of a database list model that can be used in UI's */
public interface IJdbcDatabaseListModel extends ListModel<IJdbcDatabaseListModel.Database>
{
    /** Reload databases from server */
    void reload();

    /** Definition of a database */
    interface Database
    {
        String getName();
    }
}
