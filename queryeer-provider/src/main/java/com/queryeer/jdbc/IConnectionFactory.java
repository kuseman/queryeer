package com.queryeer.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/** Definition of a connection factory */
public interface IConnectionFactory
{
    /** Get a SQL connection from provided jdbc connection */
    Connection getConnection(IJdbcConnection connection) throws SQLException;
}
