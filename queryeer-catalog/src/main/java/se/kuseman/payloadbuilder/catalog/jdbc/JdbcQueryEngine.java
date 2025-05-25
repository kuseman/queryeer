package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.Strings.CI;

import java.awt.Component;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.Icon;
import javax.swing.SwingUtilities;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.DialogUtils.IQuickSearchModel;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.editor.IEditorFactory;
import com.queryeer.api.editor.TextSelection;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.event.ShowOptionsEvent;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.output.QueryeerOutputWriter;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.api.utils.MapUtils;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DatabaseProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

/** JdbcQuery engine */
class JdbcQueryEngine implements IQueryEngine
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcQueryEngine.class);
    private static final String QUERY_NOT_CONNECTED_MESSAGE = "Query file is not connected to any data source. Right click or CTRL/META-hoover + left click on a connection or database in tree.";
    static final String TEXT_SQL = "text/sql";
    static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(BasicThreadFactory.builder()
            .daemon(true)
            .namingPattern("JdbcQueryEngine#-%d")
            .build());

    private final CatalogCrawlService crawlService;
    private final JdbcConnectionsModel connectionsModel;
    private final Icons icons;
    private final IEventBus eventBus;
    private final IEditorFactory editorFactory;
    private final DatasourcesQuickSearchModel datasourcesQuickSearchModel;
    private final IQueryFileProvider queryFileProvider;
    private final DatabaseProvider databaseProvider;
    private final JdbcConnectionsTreeConfigurable connectionsTreeConfigurable;
    private JdbcEngineQuickPropertiesComponent quickProperties;

    //@formatter:off
    JdbcQueryEngine(
            CatalogCrawlService crawlService,
            IQueryFileProvider queryFileProvider,
            Icons icons,
            JdbcConnectionsModel connectionsModel,
            DatabaseProvider databaseProvider,
            IEventBus eventBus,
            IEditorFactory editorFactory,
            DatasourcesQuickSearchModel datasourcesQuickSearchModel,
            JdbcConnectionsTreeConfigurable connectionsTreeConfigurable)
    {
        //@formatter:on
        this.crawlService = requireNonNull(crawlService, "crawlService");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.icons = requireNonNull(icons, "icons");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.databaseProvider = requireNonNull(databaseProvider, "databaseProvider");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.editorFactory = requireNonNull(editorFactory, "editorFactory");
        this.datasourcesQuickSearchModel = requireNonNull(datasourcesQuickSearchModel, "datasourcesQuickSearchModel");
        this.connectionsTreeConfigurable = requireNonNull(connectionsTreeConfigurable, "connectionsTreeConfigurable");
    }

    @Override
    public Component getQuickPropertiesComponent()
    {
        if (quickProperties == null)
        {
          //@formatter:off
            this.quickProperties = new JdbcEngineQuickPropertiesComponent(
                    this,
                    icons,
                    queryFileProvider,
                    connectionsModel,
                    eventBus,
                    databaseProvider,
                    crawlService,
                    connectionsTreeConfigurable);
            //@formatter:on
        }
        return quickProperties;
    }

    @Override
    public IState createState()
    {
        return new JdbcEngineState(this);
    }

    @Override
    public IEditor createEditor(IState state, String filename)
    {
        JdbcEngineState engineState = (JdbcEngineState) state;
        JdbcTextEditorKit editorKit = new JdbcTextEditorKit(engineState, icons.getIconFactory(), eventBus);
        return editorFactory.createTextEditor(engineState, editorKit);
    }

    @Override
    public IState cloneState(IQueryFile queryFile)
    {
        JdbcEngineState state = queryFile.getEngineState();
        if (state != null)
        {
            return state.cloneState();
        }
        return null;
    }

    @Override
    public boolean shouldExecute(IQueryFile queryFile)
    {
        // TODO: confirm multi db query
        return true;
    }

    @Override
    public void execute(IQueryFile queryFile, OutputWriter writer, Object query) throws Exception
    {
        JdbcEngineState engineState = queryFile.getEngineState();
        synchronized (engineState)
        {
            ConnectionState state = engineState.connectionState;
            JdbcDatabase jdbcDatabase = state != null ? state.getJdbcDatabase()
                    : null;
            String queryText = "";
            boolean temporaryState = false;
            ITextOutputComponent textOutput = queryFile.getOutputComponent(ITextOutputComponent.class);

            // Event query
            if (query instanceof ExecuteQueryContext ctx)
            {
                queryText = ctx.getQuery();
                if (queryText == null)
                {
                    queryText = String.valueOf(queryFile.getEditor()
                            .getValue(false));
                }

                // Execute the event query with a new connection etc.
                if (ctx.getJdbcConnection() != null)
                {
                    jdbcDatabase = ctx.getJdbcDatabase();
                    state = new ConnectionState(ctx.getJdbcConnection(), ctx.getJdbcDatabase(), ctx.getDatabase());
                    temporaryState = true;
                }
            }
            else
            {
                queryText = String.valueOf(query);
            }

            // No state present, print warning
            if (state == null)
            {
                textOutput.appendWarning(QUERY_NOT_CONNECTED_MESSAGE, TextSelection.EMPTY);

                // If no connections available, popup options dialog
                if (connectionsModel.getConnections()
                        .isEmpty())
                {
                    eventBus.publish(new ShowOptionsEvent(JdbcConnectionsConfigurable.class));
                }

                return;
            }

            executeInternal(queryFile, engineState, textOutput, writer, jdbcDatabase, queryText, state, temporaryState);
        }
    }

    @Override
    public void abortQuery(IQueryFile queryFile)
    {
        JdbcEngineState state = queryFile.getEngineState();
        if (state != null
                && state.connectionState != null)
        {
            state.connectionState.abort();
        }
    }

    @Override
    public String getTitle()
    {
        return Constants.TITLE;
    }

    @Override
    public String getDefaultFileExtension()
    {
        return "sql";
    }

    @Override
    public Icon getIcon()
    {
        return icons.database;
    }

    @Override
    public int order()
    {
        return 1;
    }

    @Override
    public void focus(IQueryFile queryFile)
    {
        if (quickProperties != null)
        {
            quickProperties.focus(queryFile);
        }
    }

    @Override
    public ExecuteQueryEvent getExecuteQueryEvent(String query, String newQueryFileName, OutputType outputType)
    {
        return new ExecuteQueryEvent(outputType, newQueryFileName, new ExecuteQueryContext(query));
    }

    @SuppressWarnings("unchecked")
    @Override
    public IQuickSearchModel<DatasourcesQuickSearchModel.DatasourceItem> getDatasourceSearchModel()
    {
        return datasourcesQuickSearchModel;
    }

    //@formatter:off
    private void executeInternal(
            IQueryFile queryFile,
            JdbcEngineState engineState,
            ITextOutputComponent textOutput,
            OutputWriter writer,
            JdbcDatabase jdbcDatabase,
            String queryText,
            ConnectionState state,
            boolean temporaryState) throws Exception
    {
        //@formatter:on

        MutableBoolean sqlError = new MutableBoolean(false);
        List<String> batches = getBatches(jdbcDatabase, queryText);
        try
        {
            // Reset the query before execution to reset abort flag etc.
            state.reset();
            Connection connection = state.getConnection();
            jdbcDatabase.beforeExecuteQuery(connection, engineState);

            // Only set status on regular queries
            if (!temporaryState)
            {
                quickProperties.setStatus(queryFile, state);
            }
            try (Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY))
            {
                state.setCurrentStatement(statement);
                for (String batch : batches)
                {
                    boolean first = true;
                    while (true)
                    {
                        int rowCount = 0;
                        long time = System.nanoTime();
                        try (ResultSet rs = JdbcUtils.getNextResultSet(e ->
                        {
                            sqlError.setTrue();
                            if (!jdbcDatabase.handleSQLException(queryFile, textOutput, e))
                            {
                                textOutput.appendWarning(e.getMessage(), TextSelection.EMPTY);
                            }
                        }, textOutput.getTextWriter(), statement, batch, first))
                        {
                            first = false;
                            // We're done!
                            if (rs == null)
                            {
                                break;
                            }

                            rowCount += writeResultSet(queryFile, connection, engineState, state, rs, writer);
                        }

                        if (!state.isAbort())
                        {
                            long total = TimeUnit.MILLISECONDS.convert(System.nanoTime() - time, TimeUnit.NANOSECONDS);
                            String output = String.format("%s%d row(s) affected, execution time: %s, server: %s", System.lineSeparator(), rowCount, DurationFormatUtils.formatDurationHMS(total),
                                    "%s / %s".formatted(state.getJdbcConnection()
                                            .getName(), state.getDatabase()));
                            textOutput.getTextWriter()
                                    .println(output);
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            // Don't show errors if the query was aborted
            if (state.isAbort())
            {
                return;
            }

            if (e instanceof SQLException sqle)
            {
                sqlError.setTrue();
                if (jdbcDatabase.handleSQLException(queryFile, textOutput, sqle))
                {
                    return;
                }
            }

            throw e;
        }
        finally
        {
            // Switch state of estimate, that is a one time action
            if (engineState.estimateQueryPlan)
            {
                engineState.estimateQueryPlan = false;
            }

            if (!temporaryState)
            {
                state.setDatabaseFromConnection();
                // Update properties component to reflect the database in connection
                SwingUtilities.invokeLater(() ->
                {
                    quickProperties.setSelectedDatabase(state);
                    quickProperties.setStatus(queryFile, state);
                });
            }
            else
            {
                EXECUTOR.submit(() -> IOUtils.closeQuietly(state));
            }

            // Focus messages
            if (sqlError.isTrue())
            {
                queryFile.focusMessages();
            }
        }
    }

    private int writeResultSet(IQueryFile queryFile, Connection connection, JdbcEngineState engineState, ConnectionState state, ResultSet rs, OutputWriter writer) throws SQLException, IOException
    {
        int rowCount = 0;
        Pair<int[], String[]> pair = getColumnsMeta(rs);
        int[] sqlTypes = pair.getKey();
        String[] columns = pair.getValue();

        if (writer instanceof QueryeerOutputWriter qwriter)
        {
            //@formatter:off
            Map<String, Object> metaData = MapUtils.ofEntries(true,
                    MapUtils.entry("Connection", state.getJdbcConnection().getName()),
                    MapUtils.entry("Database", state.getJdbcDatabase().usesSchemaAsDatabase() ? connection.getSchema()
                            : connection.getCatalog())
                    );
            //@formatter:on

            qwriter.initResult(columns, metaData);
        }
        else
        {
            writer.initResult(columns);
        }
        int count = columns.length;
        boolean first = true;

        while (rs.next())
        {
            if (state != null
                    && state.isAbort())
            {
                LOGGER.debug("Aborting query due to abort in state");
                break;
            }

            if (first)
            {
                if (!state.getJdbcDatabase()
                        .processResultSet(queryFile, engineState, rs))
                {
                    LOGGER.debug("Aborting query due to JdbcDatabase#processResultSet returned false");
                    return 0;
                }
                first = false;
            }

            writer.startRow();

            writer.startObject();
            for (int i = 0; i < count; i++)
            {
                writer.writeFieldName(columns[i]);
                writer.writeValue(JdbcUtils.getAndConvertValue(rs, i + 1, sqlTypes[i]));
            }
            writer.endObject();

            writer.endRow();

            rowCount++;
        }

        writer.endResult();

        return rowCount;
    }

    private List<String> getBatches(JdbcDatabase database, String query)
    {
        String delimiter = database.getBatchDelimiter();
        if (isBlank(delimiter))
        {
            return singletonList(query);
        }

        String[] lines = query.split("\\r?\\n");
        int count = lines.length;
        List<String> batches = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++)
        {
            String line = lines[i];

            // Batch delimiter found, add previous batch to result and start over
            if (CI.startsWith(line, delimiter))
            {
                if (sb.length() > 0)
                {
                    batches.add(sb.toString());
                }

                sb.setLength(0);
                continue;
            }

            sb.append(line)
                    .append(System.lineSeparator());
        }

        // Add last batch
        if (sb.length() > 0)
        {
            batches.add(sb.toString());
        }

        return batches;
    }

    private Pair<int[], String[]> getColumnsMeta(ResultSet rs) throws SQLException
    {
        int count = rs.getMetaData()
                .getColumnCount();
        String[] columns = new String[count];
        int[] sqlTypes = new int[count];
        for (int i = 0; i < count; i++)
        {
            columns[i] = rs.getMetaData()
                    .getColumnLabel(i + 1);
            sqlTypes[i] = rs.getMetaData()
                    .getColumnType(i + 1);
        }
        return Pair.of(sqlTypes, columns);
    }
}
