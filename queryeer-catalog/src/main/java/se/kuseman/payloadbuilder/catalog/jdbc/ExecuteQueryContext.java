package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

/** Context when executing queries internally */
public class ExecuteQueryContext
{
    private final JdbcConnection jdbcConnection;
    private final JdbcDatabase jdbcDatabase;
    private final String database;
    private final String query;

    /** Context used when a query should be executed in the current query files context with it's connection etc. */
    public ExecuteQueryContext(String query)
    {
        this.jdbcConnection = null;
        this.jdbcDatabase = null;
        this.database = null;
        this.query = query;
    }

    /** Context used when a query should be executed for a new connection/database/query combination */
    public ExecuteQueryContext(JdbcConnection jdbcConnection, JdbcDatabase jdbcDatabase, String database, String query)
    {
        this.jdbcConnection = requireNonNull(jdbcConnection);
        this.jdbcDatabase = requireNonNull(jdbcDatabase);
        this.database = requireNonNull(database);
        this.query = requireNonNull(query);
    }

    public JdbcConnection getJdbcConnection()
    {
        return jdbcConnection;
    }

    public JdbcDatabase getJdbcDatabase()
    {
        return jdbcDatabase;
    }

    public String getDatabase()
    {
        return database;
    }

    public String getQuery()
    {
        return query;
    }
}
