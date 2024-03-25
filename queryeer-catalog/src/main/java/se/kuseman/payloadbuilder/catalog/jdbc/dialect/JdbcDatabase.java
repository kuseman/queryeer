package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;

import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionState;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;

/** Definition of a JDBC database. This is the glue that is missing from plain JDBC to do quirks and specials for different RDBMS:es */
public interface JdbcDatabase// extends SqlDialect
{
    /** Name of this database. Used in configurations etc. */
    String name();

    /** Return tree node supplier for this database */
    TreeNodeSupplier getTreeNodeSupplier();

    /**
     * Handle query exception
     *
     * @return Returns true if exception was handled otherwise false
     */
    default boolean handleSQLException(IQueryFile queryFile, ITextOutputComponent textOutput, SQLException e)
    {
        return false;
    }

    /** Returns true if this dialect uses JDBC schemas as database */
    default boolean usesSchemaAsDatabase()
    {
        return false;
    }

    /**
     * Return batch delimiter for this dialect.
     *
     * @return Delimiter of null of this dialect doesn't support batches
     */
    default String getBatchDelimiter()
    {
        return null;
    }

    /** Return session for provided connection */
    default String getSessionId(Connection connection)
    {
        return "unkown";
    }

    /** Return the keyword for a session id for this database */
    default String getSessionKeyword()
    {
        return "SID";
    }

    /** Checks provided connection for validity */
    default boolean isValid(Connection connection) throws SQLException
    {
        return connection != null
                && !connection.isClosed()
                && connection.isValid(1);
    }

    /** Creates a {@link Connection} for this database */
    default Connection createConnection(String url, String username, String password) throws SQLException
    {
        return DriverManager.getConnection(url, username, password);
    }

    /** Return document parser for this database */
    default ITextEditorDocumentParser getParser(IConnectionState connectionState)
    {
        return null;
    }

    /** Return catalog meta data for provided database */
    default Catalog getCatalog(IConnectionState connectionState, String database)
    {
        return null;
    }
}
