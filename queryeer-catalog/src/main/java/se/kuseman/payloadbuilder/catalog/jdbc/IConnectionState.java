package se.kuseman.payloadbuilder.catalog.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

/** Definition of the state of a connection (query tab) */
public interface IConnectionState
{
    /** Get seleted database */
    String getDatabase();

    /** Return the jdbc database instance for this state */
    JdbcDatabase getJdbcDatabase();

    /** Get connection in the state */
    JdbcConnection getJdbcConnection();

    /** Create a new {@link Connection} for this state */
    Connection createConnection() throws SQLException;

    /** Returns true if the query should include a query plan. */
    boolean isIncludeQueryPlan();

    /** Returns true if the query should include an estimated query plan. */
    boolean isEstimateQueryPlan();
}
