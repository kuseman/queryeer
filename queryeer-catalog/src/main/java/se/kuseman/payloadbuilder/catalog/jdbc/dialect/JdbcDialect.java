package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;

import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionContext;
import se.kuseman.payloadbuilder.catalog.jdbc.IJdbcEngineState;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Routine;
import se.kuseman.payloadbuilder.catalog.jdbc.monitor.IServerMonitorExtension;

/** Definition of a JDBC dialect. This is the glue that is missing from plain JDBC to do quirks and specials for different RDBMS:es */
public interface JdbcDialect
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

    /**
     * Create connection with provided url and credentials.
     *
     * @param importMode If this connection is intended to be used when importing data and the dialect supports special options to make that faster this can be set to true.
     */
    default Connection createConnection(String url, String username, String password, boolean importMode) throws SQLException
    {
        return DriverManager.getConnection(url, username, password);
    }

    /** Return document parser for this database */
    default ITextEditorDocumentParser getParser(IConnectionContext connectionContext)
    {
        return null;
    }

    /**
     * Parse the provided query text and return all table source {@link ObjectName}s referenced in it. Returns an empty list if the dialect does not support ANTLR-based parsing.
     */
    default List<ObjectName> getReferencedTableSources(String queryText, IConnectionContext connectionContext)
    {
        ITextEditorDocumentParser docParser = getParser(connectionContext);
        if (!(docParser instanceof AntlrDocumentParser<?> antlrParser))
        {
            return Collections.emptyList();
        }
        try
        {
            antlrParser.parse(new StringReader(queryText));
            return antlrParser.getReferencedTableSources();
        }
        catch (Exception e)
        {
            return Collections.emptyList();
        }
    }

    /**
     * Parse the provided routine body and return all routine {@link ObjectName}s called in it. Returns an empty list if the dialect does not support ANTLR-based parsing.
     */
    default List<ObjectName> getReferencedRoutines(String routineBody, IConnectionContext connectionContext)
    {
        ITextEditorDocumentParser docParser = getParser(connectionContext);
        if (!(docParser instanceof AntlrDocumentParser<?> antlrParser))
        {
            return Collections.emptyList();
        }
        try
        {
            antlrParser.parse(new StringReader(routineBody));
            return antlrParser.getReferencedRoutines();
        }
        catch (Exception e)
        {
            return Collections.emptyList();
        }
    }

    /**
     * Extract all routine calls from every entry in {@code bodies} using a single parser instance and lightweight parsing (no code-completion init, no semantic validation). Returns a map from the
     * same keys as {@code bodies} to the list of routine {@link ObjectName}s called by that routine.
     */
    default Map<String, List<ObjectName>> extractAllRoutineCalls(Map<String, String> bodies, IConnectionContext connectionContext)
    {
        ITextEditorDocumentParser docParser = getParser(connectionContext);
        if (!(docParser instanceof AntlrDocumentParser<?> antlrParser))
        {
            return Collections.emptyMap();
        }
        Map<String, List<ObjectName>> result = new java.util.HashMap<>();
        for (Map.Entry<String, String> entry : bodies.entrySet())
        {
            try
            {
                antlrParser.parseLight(new StringReader(entry.getValue()));
                List<ObjectName> calls = antlrParser.getReferencedRoutines();
                if (!calls.isEmpty())
                {
                    result.put(entry.getKey(), calls);
                }
            }
            catch (Exception e)
            {
                // skip unparseable routine body
            }
        }
        return result;
    }

    /**
     * Fetch the SQL source bodies for all provided routines in a single operation. Returns a map keyed by lower-case {@code schema.name} (or just {@code name} when schema is blank) to the routine
     * definition. Routines with no retrievable body are omitted from the map.
     */
    default Map<String, String> getRoutineBodies(IConnectionContext connectionContext, List<Routine> routines)
    {
        return Collections.emptyMap();
    }

    /** Return catalog meta data for provided database */
    default Catalog getCatalog(IConnectionContext connectionContext, String database)
    {
        return null;
    }

    /** Called before query execution to let dialect perform init. operations like include query plans etc. */
    default void beforeExecuteQuery(Connection connection, IJdbcEngineState engineState) throws SQLException
    {
        return;
    }

    /**
     * Process result set and perform actions. NOTE! Result set is placed on first row when passed.
     *
     * @return Return true if engine should proceed and write result set to output otherwise false. When false this result set is discarded.
     */
    default boolean processResultSet(IQueryFile queryFile, IJdbcEngineState engineState, ResultSet rs) throws SQLException
    {
        return true;
    }

    /**
     * Returns true if this database supports show estimated query plan action.
     */
    default boolean supportsShowEstimatedQueryPlanAction()
    {
        return false;
    }

    /**
     * Returns true if this database supports show estimated query plan action.
     */
    default boolean supportsIncludeQueryPlanAction()
    {
        return false;
    }

    /**
     * Returns the server monitor extension for this dialect, or null if monitoring is not supported.
     */
    default IServerMonitorExtension getMonitorExtension()
    {
        return null;
    }

    /**
     * Returns a value for a result set ordinal.
     */
    default Object getJdbcValue(ResultSet rs, int ordinal, int jdbcType) throws Exception
    {
        if (jdbcType == java.sql.Types.CLOB)
        {
            Reader reader = rs.getCharacterStream(ordinal);
            if (rs.wasNull())
            {
                return null;
            }
            return IOUtils.toString(reader);
        }

        return rs.getObject(ordinal);
    }
}
