package com.queryeer.provider.jdbc;

import static java.util.Objects.requireNonNull;

import java.awt.Component;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import javax.swing.SwingUtilities;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.IQueryFile.ExecutionState;
import com.queryeer.api.IQueryFileState;
import com.queryeer.api.component.IIconProvider;
import com.queryeer.api.component.ISyntaxTextEditor;
import com.queryeer.api.extensions.IQueryProvider;
import com.queryeer.api.service.IComponentFactory;
import com.queryeer.jdbc.IConnectionFactory;
import com.queryeer.jdbc.IJdbcConnection;
import com.queryeer.jdbc.IJdbcConnectionListModel;
import com.queryeer.jdbc.IJdbcDatabaseListModel;
import com.queryeer.jdbc.IJdbcDatabaseListModelFactory;
import com.queryeer.jdbc.Utils;
import com.queryeer.jdbc.Utils.CredentialsResult;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** JDBC query provider */
class JdbcQueryProvider implements IQueryProvider
{
    private final JdbcQuickProperties quickProperties;
    private final IComponentFactory componentFactory;
    private final IConnectionFactory connectionFactory;
    private final IJdbcConnectionListModel connectionsListModel;
    private final IJdbcDatabaseListModelFactory databaseModelFactory;
    private final IIconProvider iconProvider;

    JdbcQueryProvider(JdbcQuickProperties quickProperties, IComponentFactory componentFactory, IConnectionFactory connectionFactory, IJdbcConnectionListModel connectionsListModel,
            IJdbcDatabaseListModelFactory databaseModelFactory, IIconProvider iconProvider)
    {
        this.quickProperties = requireNonNull(quickProperties, "quickProperties");
        this.componentFactory = requireNonNull(componentFactory, "componentFactory");
        this.connectionFactory = requireNonNull(connectionFactory, "connectionFactory");
        this.connectionsListModel = requireNonNull(connectionsListModel, "connectionsListModel");
        this.databaseModelFactory = requireNonNull(databaseModelFactory, "databaseModelFactory");
        this.iconProvider = requireNonNull(iconProvider, "iconProvider");
    }

    @Override
    public String getTitle()
    {
        return "JDBC";
    }

    @Override
    public String getFilenameExtension()
    {
        return "sql";
    }

    @Override
    public IQueryFileState createQueryFileState()
    {
        ISyntaxTextEditor textEditor = componentFactory.createSyntaxTextEditor("text/sql");
        JdbcQueryEditor editor = new JdbcQueryEditor(iconProvider, textEditor, connectionsListModel, databaseModelFactory);
        JdbcQueryFileState state = new JdbcQueryFileState(this, editor);
        editor.setState(state);
        return state;
    }

    @Override
    public void executeQuery(IQueryFile queryFile, OutputWriter writer)
    {
        Optional<JdbcQueryFileState> fileState = queryFile.getQueryFileState(JdbcQueryFileState.class);
        String query = fileState.get()
                .getTextEditor()
                .getModel()
                .getText(true);
        PrintWriter messagesWriter = queryFile.getMessagesWriter();

        IJdbcConnection connection = fileState.get()
                .getSelectedJdbcConnection();

        if (connection == null)
        {
            messagesWriter.println("No connection selected");
            queryFile.focusMessages();
            return;
        }

        IJdbcDatabaseListModel.Database database = fileState.get()
                .getSelectedDatabase();
        BooleanSupplier abortSupplier = () -> queryFile.getExecutionState() == ExecutionState.ABORTED;

        // TODO: multi connection query

        performQuery(connection, database, fileState.get(), query, messagesWriter, writer, abortSupplier);
    }

    @Override
    public Component getQuickPropertiesComponent()
    {
        return quickProperties;
    }

    private void performQuery(IJdbcConnection connection, IJdbcDatabaseListModel.Database database, JdbcQueryFileState fileState, String query, PrintWriter messagesWriter, OutputWriter writer,
            BooleanSupplier abortSupplier)
    {
        CredentialsResult credentialsResult = Utils.ensureCredentials(connection);
        if (credentialsResult == CredentialsResult.CANCELLED)
        {
            return;
        }

        try (Connection con = connectionFactory.getConnection(connection))
        {
            if (database != null)
            {
                con.setCatalog(database.getName());
            }

            PreparedStatement statement = con.prepareStatement(query);
            statement.setFetchSize(1000);
            boolean result = statement.execute();
            SQLWarning statementWarning = null;
            boolean complete = false;
            while (!complete)
            {
                statementWarning = printWarns(statement.getWarnings(), statementWarning, messagesWriter);
                if (result)
                {
                    try (ResultSet rs = statement.getResultSet())
                    {
                        if (abortSupplier.getAsBoolean())
                        {
                            statement.cancel();
                            complete = true;
                            break;
                        }

                        int colSize = rs.getMetaData()
                                .getColumnCount();
                        String[] columns = new String[colSize];
                        int[] types = new int[colSize];
                        for (int i = 0; i < colSize; i++)
                        {
                            columns[i] = rs.getMetaData()
                                    .getColumnName(i + 1);
                            types[i] = rs.getMetaData()
                                    .getColumnType(i + 1);
                        }

                        writer.initResult(columns);

                        while (rs.next())
                        {
                            if (abortSupplier.getAsBoolean())
                            {
                                statement.cancel();
                                complete = true;
                                break;
                            }

                            writer.startRow();
                            writer.startObject();
                            for (int i = 0; i < colSize; i++)
                            {
                                writer.writeFieldName(columns[i]);
                                Object value = ResultSetUtils.transform(rs, i + 1, types[i]);
                                writer.writeValue(value);
                            }
                            writer.endObject();
                            writer.endRow();
                        }

                        writer.flush();
                        writer.endResult();

                        printWarns(rs.getWarnings(), null, messagesWriter);
                        statementWarning = printWarns(statement.getWarnings(), statementWarning, messagesWriter);
                    }
                }
                else
                {
                    int updateCount = statement.getUpdateCount();
                    statementWarning = printWarns(statement.getWarnings(), statementWarning, messagesWriter);
                    if (updateCount == -1)
                    {
                        complete = true;
                        break;
                    }
                    else if (updateCount > 0)
                    {
                        messagesWriter.println(String.format("%d rows affected", updateCount));
                    }
                }
                result = statement.getMoreResults();
            }

            statementWarning = printWarns(statement.getWarnings(), statementWarning, messagesWriter);
            printWarns(con.getWarnings(), null, messagesWriter);

            // Update selected database
            if (con.getCatalog() != null)
            {
                fileState.setSelectedDatabase(con.getCatalog());
            }
        }
        catch (SQLException e)
        {
            // Clear password upon exception if they were provided on this call
            if (credentialsResult == CredentialsResult.CREDENTIALS_PROVIDED)
            {
                connection.setPassword(null);
            }

            throw new RuntimeException(e);
        }
    }

    /** Print warns return the last non null warn in chain */
    private SQLWarning printWarns(SQLWarning root, SQLWarning warn, PrintWriter messagesWriter)
    {
        SQLWarning w = warn;
        if (w == null)
        {
            // Start from root
            w = root;
            warn = root;
        }
        else
        {
            // Move one step ahead
            w = warn.getNextWarning();
            if (w != null)
            {
                warn = w;
            }
        }

        // Traverse and print returning the last non null
        while (w != null)
        {
            try
            {
                String message = w.getMessage();
                SwingUtilities.invokeAndWait(() -> messagesWriter.println(message));
            }
            catch (InterruptedException | InvocationTargetException e)
            {
                e.printStackTrace(messagesWriter);
            }
            w = w.getNextWarning();
            if (w != null)
            {
                warn = w;
            }
        }
        return warn;
    }
}
