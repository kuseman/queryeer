package com.queryeer.jdbc;

import java.util.List;

import javax.swing.ListModel;

/** List model for {@link IJdbcConnection}'s */
public interface IJdbcConnectionListModel extends ListModel<IJdbcConnection>
{
    /** Get connections in this model */
    List<IJdbcConnection> getConnections();
}
