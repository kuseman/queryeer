package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.tree.TreeSelectionModel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.QueryFileMetaData;
import com.queryeer.api.component.AutoCompletionComboBox;
import com.queryeer.api.component.QueryeerTree;
import com.queryeer.api.component.QueryeerTree.RegularNode;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.editor.IEditorFactory;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.editor.TextSelection;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.event.NewQueryFileEvent;
import com.queryeer.api.event.ShowOptionsEvent;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnectionsTreeModel.ConnectionNode;
import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnectionsTreeModel.DatabaseNode;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DatabaseProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

/** JdbcQuery engine */
class JdbcQueryEngine implements IQueryEngine
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcQueryEngine.class);
    private static final String QUERY_NOT_CONNECTED_MESSAGE = "Query file is not connected to any data source. Right Click Or CTRL + Left click on a connection in tree.";
    static final String TEXT_SQL = "text/sql";
    static final String CATALOG_ALIAS = "JdbcQueryEngine";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BasicThreadFactory.Builder().daemon(true)
            .namingPattern("JdbcEngine#-%d")
            .build());

    private final CatalogCrawlService crawlService;
    private final JdbcConnectionsModel connectionsModel;
    private final JdbcConnectionsTreeModel connectionsTreeModel;
    private final Icons icons;
    private final IEventBus eventBus;
    private final IQueryFileProvider queryFileProvider;
    private final DatabaseProvider databaseProvider;
    private final IEditorFactory editorFactory;

    private JPanel quickProperties;
    private JComboBox<String> databases;
    private DefaultComboBoxModel<String> databasesModel;
    private boolean suppressEvents = false;

    JdbcQueryEngine(CatalogCrawlService crawlService, IQueryFileProvider queryFileProvider, Icons icons, JdbcConnectionsModel connectionsModel, DatabaseProvider databaseProvider, IEventBus eventBus,
            IEditorFactory editorFactory)
    {
        this.crawlService = requireNonNull(crawlService, "crawlService");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.icons = requireNonNull(icons, "icons");
        this.databaseProvider = requireNonNull(databaseProvider, "databaseProvider");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.editorFactory = requireNonNull(editorFactory, "editorFactory");
        this.connectionsTreeModel = new JdbcConnectionsTreeModel(connectionsModel, icons, databaseProvider, node -> newQuery(node));
    }

    @Override
    public Component getQuickPropertiesComponent()
    {
        if (quickProperties == null)
        {
            quickProperties = new JPanel(new GridBagLayout());

            databases = new JComboBox<>();
            databases.setPreferredSize(new Dimension(Integer.MAX_VALUE, 18));
            databases.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
            databases.setMinimumSize(new Dimension(Integer.MAX_VALUE, 18));
            databases.setRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    label.setIcon(icons.database);
                    return label;
                }
            });
            databases.setMaximumRowCount(40);
            databases.addItemListener(l ->
            {
                if (suppressEvents)
                {
                    return;
                }
                IQueryFile currentFile = queryFileProvider.getCurrentFile();
                if (l.getItem() instanceof String
                        && currentFile != null)
                {
                    JdbcEngineState engineState = (JdbcEngineState) currentFile.getEngineState();
                    ConnectionState state = engineState.connectionState;

                    if (state != null)
                    {
                        // TODO: Do not run this on EDT
                        state.setDatabaseOnConnection((String) l.getItem(), currentFile);
                        setStatus(currentFile, state);
                    }

                    engineState.resetParser();

                    // Re-parse content when we switch database
                    if (currentFile.getEditor() instanceof ITextEditor textEditor)
                    {
                        textEditor.parse();
                    }
                }
            });

            databases.setEditable(false);
            databasesModel = new DefaultComboBoxModel<>();
            databases.setModel(databasesModel);
            AutoCompletionComboBox.enable(databases);

            quickProperties.add(databases, new GridBagConstraints(0, 0, 1, 1, 1.0d, 0.0d, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 2));

            JButton changeConnection = new JButton();
            changeConnection.setMaximumSize(new Dimension(18, 18));
            changeConnection.setMinimumSize(new Dimension(18, 18));
            changeConnection.setPreferredSize(new Dimension(18, 18));
            changeConnection.setIcon(icons.plug);
            changeConnection.setToolTipText("Change Connection");
            quickProperties.add(changeConnection, new GridBagConstraints(1, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 2));

            changeConnection.addActionListener(l ->
            {
                IQueryFile currentFile = queryFileProvider.getCurrentFile();
                if (currentFile == null)
                {
                    return;
                }

                if (connectionsModel.getSize() == 0)
                {
                    eventBus.publish(new ShowOptionsEvent(JdbcConnectionsConfigurable.class));
                    return;
                }

                Object[] values = connectionsModel.getConnections()
                        .toArray();

                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();
                JdbcConnection result = (JdbcConnection) JOptionPane.showInputDialog(activeWindow, "Connection", "Change Connection", JOptionPane.QUESTION_MESSAGE, null, values, values[0]);

                if (result != null
                        && connectionsModel.prepare(result, false))
                {

                    JdbcDatabase jdbcDatabase = databaseProvider.getDatabase(result.getJdbcURL());
                    JdbcEngineState engineState = (JdbcEngineState) currentFile.getEngineState();
                    ConnectionState state = engineState.connectionState;

                    // Close old state if any
                    if (state != null)
                    {
                        EXECUTOR.execute(() ->
                        {
                            try
                            {
                                state.close();
                            }
                            catch (Exception e)
                            {
                                // SWALLOW
                            }
                        });
                    }

                    // Create new state
                    ConnectionState newState = new ConnectionState(result, jdbcDatabase);
                    engineState.setConnectionState(newState);
                    focus(currentFile);
                    // Lazy load databases
                    loadConnectionMetaData(engineState);
                }
            });

            final QueryeerTree.QueryeerTreeModel treeModel = new QueryeerTree.QueryeerTreeModel(connectionsTreeModel);
            connectionsModel.addListDataListener(new ListDataListener()
            {
                @Override
                public void intervalRemoved(ListDataEvent e)
                {
                }

                @Override
                public void intervalAdded(ListDataEvent e)
                {
                }

                @Override
                public void contentsChanged(ListDataEvent e)
                {
                    // Reload root when connections model changes
                    treeModel.resetRoot();
                }
            });

            QueryeerTree tree = new QueryeerTree(treeModel);
            tree.getSelectionModel()
                    .setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
            tree.setHyperLinksEnabled(true);
            quickProperties.add(new JScrollPane(tree), new GridBagConstraints(0, 1, 2, 1, 1.0d, 1.0d, GridBagConstraints.SOUTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
            quickProperties.setPreferredSize(new Dimension(240, 75));
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
        JdbcEngineState state = (JdbcEngineState) queryFile.getEngineState();
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
        JdbcEngineState engineState = (JdbcEngineState) queryFile.getEngineState();
        synchronized (engineState)
        {
            ConnectionState state = engineState.connectionState;
            JdbcDatabase jdbcDatabase;
            String queryText;
            boolean closeState = false;
            ITextOutputComponent textOutput = queryFile.getOutputComponent(ITextOutputComponent.class);

            // Internal query
            if (query instanceof ExecuteQueryContext ctx)
            {
                queryText = ctx.getQuery();
                if (queryText == null)
                {
                    queryText = String.valueOf(queryFile.getEditor()
                            .getValue(false));
                }

                // Execute the context query with a new connection etc.
                if (ctx.getJdbcConnection() != null)
                {
                    jdbcDatabase = ctx.getJdbcDatabase();
                    state = new ConnectionState(ctx.getJdbcConnection(), ctx.getJdbcDatabase(), ctx.getDatabase());
                    closeState = true;
                }
                // .. else use the current files connection etc.
                else
                {
                    jdbcDatabase = state.getJdbcDatabase();
                }
            }
            else
            {
                if (state == null)
                {
                    textOutput.appendWarning(QUERY_NOT_CONNECTED_MESSAGE, TextSelection.EMPTY);

                    // If no connetions available, popup options dialog
                    if (connectionsModel.getConnections()
                            .isEmpty())
                    {
                        eventBus.publish(new ShowOptionsEvent(JdbcConnectionsConfigurable.class));
                    }

                    return;
                }
                queryText = String.valueOf(query);
                jdbcDatabase = state.getJdbcDatabase();
            }

            MutableBoolean sqlError = new MutableBoolean(false);
            List<String> batches = getBatches(jdbcDatabase, queryText);
            try
            {
                Connection connection = state.getConnection();
                jdbcDatabase.beforeExecuteQuery(connection, engineState);

                // Only set status on regular queries
                if (!closeState)
                {
                    setStatus(queryFile, state);
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

                                rowCount += writeResultSet(queryFile, rs, writer, engineState);
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

                // Update properties component to reflect the catalog in connection
                if (!closeState)
                {
                    state.setDatabaseFromConnection(queryFile);
                }
            }
            catch (Exception e)
            {
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

                if (!closeState)
                {
                    state.afterQuery();
                    databasesModel.setSelectedItem(state.getDatabase());
                }
                else
                {
                    try
                    {
                        // Close the temporary state
                        state.close();
                    }
                    catch (IOException e)
                    {
                        LOGGER.error("Error closing temporary state", e);
                    }
                }

                // Focus messages
                if (sqlError.isTrue())
                {
                    queryFile.focusMessages();
                }
            }
        }
    }

    @Override
    public void abortQuery(IQueryFile queryFile)
    {
        JdbcEngineState state = (JdbcEngineState) queryFile.getEngineState();
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
        if (quickProperties == null
                || databasesModel == null)
        {
            return;
        }

        suppressEvents = true;

        try
        {
            databasesModel.removeAllElements();
            databases.setEnabled(false);
            JdbcEngineState engineState = (JdbcEngineState) queryFile.getEngineState();
            ConnectionState state = engineState.connectionState;
            if (state != null)
            {
                // Force a reparse when we focus a new file
                if (queryFile.getEditor() instanceof ITextEditor textEditor)
                {
                    if (textEditor.getEditorKit() instanceof JdbcTextEditorKit kit)
                    {
                        kit.updateActionStatuses();
                    }
                    textEditor.parse();
                }

                if (state.getJdbcConnection()
                        .getDatabases() != null)
                {
                    databasesModel.addAll(state.getJdbcConnection()
                            .getDatabases());
                    databasesModel.setSelectedItem(state.getDatabase());
                    databases.setEnabled(true);
                }

                setStatus(queryFile, state);
            }
            else
            {
                queryFile.setMetaData(new QueryFileMetaData("<html><font color=\"#000000\"><b>Not connected</b></font></html>", ""));
            }
        }
        finally
        {
            suppressEvents = false;
        }
    }

    @Override
    public ExecuteQueryEvent getExecuteQueryEvent(String query, String newQueryFileName, OutputType outputType)
    {
        return new ExecuteQueryEvent(outputType, newQueryFileName, new ExecuteQueryContext(query));
    }

    private int writeResultSet(IQueryFile queryFile, ResultSet rs, OutputWriter writer, JdbcEngineState state) throws SQLException, IOException
    {
        int rowCount = 0;
        Pair<int[], String[]> pair = getColumnsMeta(rs);
        int[] sqlTypes = pair.getKey();
        String[] columns = pair.getValue();
        writer.initResult(columns);

        int count = columns.length;
        boolean first = true;

        while (rs.next())
        {
            if (state.connectionState != null
                    && state.connectionState.isAbort())
            {
                break;
            }

            if (first)
            {
                if (state.getJdbcDatabase() != null
                        && !state.getJdbcDatabase()
                                .processResultSet(queryFile, state, rs))
                {
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

    private void newQuery(RegularNode node)
    {
        ConnectionState state = null;
        if (node instanceof ConnectionNode cnode)
        {
            state = cnode.createState();
        }
        else if (node instanceof DatabaseNode dnode)
        {
            state = dnode.createState();
        }

        if (state != null
                && connectionsModel.prepare(state.getJdbcConnection(), false))
        {
            JdbcEngineState engineState = new JdbcEngineState(this, state);
            eventBus.publish(new NewQueryFileEvent(JdbcQueryEngine.this, engineState, null, false, null));
            // Lazy load databases
            loadConnectionMetaData(engineState);
        }
    }

    private void loadConnectionMetaData(JdbcEngineState state)
    {
        if (state.getJdbcConnection()
                .getDatabases() == null)
        {
            EXECUTOR.execute(() ->
            {
                connectionsModel.getDatabases(state.getJdbcConnection(), CATALOG_ALIAS, true, false);

                IQueryFile currentFile = queryFileProvider.getCurrentFile();
                if (currentFile != null)
                {
                    JdbcEngineState currentEngineState = (JdbcEngineState) currentFile.getEngineState();
                    ConnectionState currentState = currentEngineState.connectionState;
                    // If the current file is the same as the one we loaded databases on then re-focus it
                    if (currentState != null
                            && currentState.getJdbcConnection() == state.getJdbcConnection())
                    {
                        SwingUtilities.invokeLater(() -> focus(currentFile));
                    }
                }
            });
        }
        if (state.getDatabase() != null)
        {
            crawlService.getCatalog(state, state.getDatabase());
        }
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
            if (StringUtils.startsWithIgnoreCase(line, delimiter))
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

    private void setStatus(IQueryFile file, ConnectionState state)
    {
        String connectionInfo = "<html><font color=\"%s\"><b>%s: %s / %s%s / %s</b></font>".formatted(Objects.toString(state.getJdbcConnection()
                .getColor(), "#000000"), state.getJdbcDatabase()
                        .getSessionKeyword(),
                isBlank(state.getSessionId()) ? "Not connected"
                        : state.getSessionId(),
                state.getJdbcConnection()
                        .getName(),
                (!isBlank(state.getDatabase()) ? " / " + state.getDatabase()
                        : ""),
                state.getJdbcConnection()
                        .getUsername());

        file.setMetaData(new QueryFileMetaData(connectionInfo, state.getSessionId()));
    }
}
