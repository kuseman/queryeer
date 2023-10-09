package se.kuseman.payloadbuilder.catalog.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/** Connection supplier */
public interface SqlConnectionSupplier
{
    /** Create a SQL connection */
    Connection get(JdbcConnection jdbcConnection) throws SQLException;
}
