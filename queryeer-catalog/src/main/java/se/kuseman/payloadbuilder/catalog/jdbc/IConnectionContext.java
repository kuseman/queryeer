package se.kuseman.payloadbuilder.catalog.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;

/** Core connection identity and factory: the minimal context needed to identify and open a JDBC connection. */
public interface IConnectionContext
{
    /** Get selected database */
    String getDatabase();

    /** Return the jdbc dialect instance for this context */
    JdbcDialect getJdbcDialect();

    /** Get the connection definition */
    JdbcConnection getJdbcConnection();

    /** Create a new {@link Connection} from this context. */
    Connection createConnection() throws SQLException;
}
