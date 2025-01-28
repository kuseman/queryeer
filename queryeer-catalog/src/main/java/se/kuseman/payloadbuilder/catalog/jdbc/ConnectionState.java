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
    private Connection connection;
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

    /** Returns the peristed connection in this state or creates a new one if not valid */
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
            connection = jdbcDatabase.createConnection(jdbcConnection.getJdbcURL(), jdbcConnection.getUsername(), new String(jdbcConnection.getRuntimePassword()));
            sessionId = jdbcDatabase.getSessionId(connection);
            if (database == null)
            {
                setDatabaseFromConnection(null);
            }
            else
            {
                setDatabaseOnConnection(database, null);
            }
        }
        return connection;
    }

    Connection createConnection() throws SQLException
    {
        Connection connection = jdbcDatabase.createConnection(jdbcConnection.getJdbcURL(), jdbcConnection.getUsername(), new String(jdbcConnection.getRuntimePassword()));
        if (database != null)
        {
            if (jdbcDatabase.usesSchemaAsDatabase())
            {
                connection.setSchema(database);
            }
            else
            {
                connection.setCatalog(database);
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

    void setDatabaseFromConnection(IQueryFile queryFile)
    {
        try
        {
            if (connection == null)
            {
                getConnection();
            }
            this.database = jdbcDatabase.usesSchemaAsDatabase() ? connection.getSchema()
                    : connection.getCatalog();
        }
        catch (SQLException e)
        {
            if (queryFile != null)
            {
                queryFile.getMessagesWriter()
                        .println(e.getMessage());
            }
        }
    }

    void setDatabaseOnConnection(String database, IQueryFile queryFile)
    {
        try
        {
            if (jdbcDatabase.isValid(connection))
            {
                if (jdbcDatabase.usesSchemaAsDatabase())
                {
                    connection.setSchema(database);
                }
                else
                {
                    connection.setCatalog(database);
                }
            }
            this.database = database;
        }
        catch (SQLException e)
        {
            if (queryFile != null)
            {
                queryFile.getMessagesWriter()
                        .println(e.getMessage());
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
