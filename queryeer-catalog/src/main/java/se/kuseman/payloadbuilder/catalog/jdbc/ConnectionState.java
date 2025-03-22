package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.queryeer.api.IQueryFile;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

/** Connection state of a {@link IQueryFile}. Contains an open connection a selected database etc. */
class ConnectionState implements Closeable
{
    private final JdbcConnection jdbcConnection;
    private final JdbcDatabase jdbcDatabase;

    private String sessionId;
    private String database;
    private volatile Connection connection;
    // Current running statement
    private volatile Statement currentStatement;
    private volatile boolean abort;

    ConnectionState(JdbcConnection jdbcConnection, JdbcDatabase jdbcDatabase)
    {
        this(jdbcConnection, jdbcDatabase, null);
    }

    ConnectionState(JdbcConnection jdbcConnection, JdbcDatabase jdbcDatabase, String database)
    {
        this.jdbcDatabase = requireNonNull(jdbcDatabase);
        this.jdbcConnection = requireNonNull(jdbcConnection);
        this.database = database;
    }

    /** Create a new connection. */
    Connection createConnection() throws SQLException
    {
        String password = jdbcConnection.getRuntimePassword() != null ? new String(jdbcConnection.getRuntimePassword())
                : "";
        return jdbcDatabase.createConnection(jdbcConnection.getJdbcURL(), jdbcConnection.getUsername(), password);
    }

    /**
     * Returns the peristed connection in this state or creates a new one if not valid
     *
     * @param setDatabase Should the states database be set on connection
     */
    Connection getConnection() throws SQLException
    {
        boolean isValid;
        try
        {
            isValid = jdbcDatabase.isValid(connection);
        }
        catch (Exception e)
        {
            // SWALLOW
            isValid = false;
        }

        // Create a new connection if needed
        if (!isValid)
        {
            connection = createConnection();
            sessionId = jdbcDatabase.getSessionId(connection);
            if (database == null)
            {
                setDatabaseFromConnection();
            }
            else
            {
                setDatabaseOnConnection(database);
            }
        }
        return connection;
    }

    JdbcConnection getJdbcConnection()
    {
        return jdbcConnection;
    }

    JdbcDatabase getJdbcDatabase()
    {
        return jdbcDatabase;
    }

    void setCurrentStatement(Statement statement)
    {
        this.currentStatement = statement;
    }

    String getSessionId()
    {
        return sessionId;
    }

    void setDatabaseFromConnection() throws SQLException
    {
        if (!jdbcDatabase.isValid(connection))
        {
            return;
        }
        this.database = jdbcDatabase.usesSchemaAsDatabase() ? connection.getSchema()
                : connection.getCatalog();
    }

    void setDatabaseOnConnection(String database) throws SQLException
    {
        try
        {
            Connection connection = getConnection();
            if (jdbcDatabase.usesSchemaAsDatabase())
            {
                connection.setSchema(database);
            }
            else
            {
                connection.setCatalog(database);
            }
            this.database = database;
        }
        catch (SQLException e)
        {
            // If we encounter problems here due to missing rights or whatever, put back the database from
            // what the connection has
            setDatabaseFromConnection();
            // .. and let the original error bubble up so UI etc. is notified
            // only do that for valid connections to avoid socket closed error etc. to bubble up
            if (jdbcDatabase.isValid(connection))
            {
                throw e;
            }
        }
    }

    void abort()
    {
        abort = true;
        JdbcUtils.cancelQuiet(currentStatement);
    }

    boolean isAbort()
    {
        return abort;
    }

    void reset()
    {
        abort = false;
        currentStatement = null;
    }

    String getDatabase()
    {
        return database;
    }

    @Override
    public void close() throws IOException
    {
        Connection con = connection;
        JdbcUtils.rollbackQuiet(con);
        JdbcUtils.closeQuiet(con);
    }

    ConnectionState cloneState()
    {
        return new ConnectionState(jdbcConnection, jdbcDatabase, database);
    }
}
