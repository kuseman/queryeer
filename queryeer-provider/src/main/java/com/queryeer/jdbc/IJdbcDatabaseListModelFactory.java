package com.queryeer.jdbc;

/** Factory for creating database list models */
public interface IJdbcDatabaseListModelFactory
{
    /** Get database model from connection */
    IJdbcDatabaseListModel getModel(IJdbcConnection connection);
}
