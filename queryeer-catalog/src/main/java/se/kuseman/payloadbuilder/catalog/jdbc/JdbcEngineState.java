package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.IQueryEngine.IState;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;

/** JDBC engine state holding connection and query plan configuration for a query file. */
class JdbcEngineState implements IJdbcEngineState
{
    private final IQueryEngine queryEngine;

    private List<Runnable> changeListeners;
    final TextEditorDocumentParserProxy documentParser;
    ConnectionContext connectionContext;

    boolean includeQueryPlan;
    boolean estimateQueryPlan;

    JdbcEngineState(IQueryEngine queeryEngine)
    {
        this(queeryEngine, null);
    }

    JdbcEngineState(IQueryEngine queryEngine, ConnectionContext connectionContext)
    {
        this.queryEngine = requireNonNull(queryEngine);
        this.connectionContext = connectionContext;
        this.documentParser = new TextEditorDocumentParserProxy();

        resetParser();
    }

    IState cloneState()
    {
        JdbcEngineState newState = new JdbcEngineState(queryEngine);
        if (connectionContext != null)
        {
            newState.setConnectionContext(connectionContext.cloneState());
        }
        return newState;
    }

    void setConnectionContext(ConnectionContext connectionContext)
    {
        this.connectionContext = connectionContext;
        documentParser.currentParser = null;
        resetParser();

        if (changeListeners != null)
        {
            for (Runnable r : new ArrayList<>(changeListeners))
            {
                r.run();
            }
        }
    }

    void resetParser()
    {
        // Switch parser if we switched state
        if (connectionContext != null)
        {
            documentParser.currentParser = connectionContext.getJdbcDialect()
                    .getParser(this);

        }
    }

    @Override
    public void addChangeListener(Runnable r)
    {
        if (changeListeners == null)
        {
            changeListeners = new ArrayList<>();
        }
        changeListeners.add(r);
    }

    @Override
    public void removeChangeListener(Runnable r)
    {
        if (changeListeners == null)
        {
            return;
        }
        changeListeners.remove(r);
    }

    @Override
    public boolean isIncludeQueryPlan()
    {
        return includeQueryPlan;
    }

    @Override
    public boolean isEstimateQueryPlan()
    {
        return estimateQueryPlan;
    }

    // IState

    @Override
    public IQueryEngine getQueryEngine()
    {
        return queryEngine;
    }

    @Override
    public List<MetaParameter> getMetaParameters(boolean testData)
    {
        String url = "jdbc://server/database";
        String database = "database";
        if (!testData)
        {
            url = connectionContext != null ? Objects.toString(connectionContext.getJdbcConnection()
                    .getJdbcURL(), "")
                    : "";
            database = connectionContext != null ? connectionContext.getDatabase()
                    : "";
        }

        return IJdbcEngineState.getMetaParameters(url, database);
    }

    @Override
    public void close() throws IOException
    {
        if (connectionContext != null)
        {
            connectionContext.close();
        }
    }

    // IState

    @Override
    public String getDatabase()
    {
        if (connectionContext != null)
        {
            return connectionContext.getDatabase();
        }
        return null;
    }

    @Override
    public JdbcDialect getJdbcDialect()
    {
        if (connectionContext != null)
        {
            return connectionContext.getJdbcDialect();
        }
        return null;
    }

    @Override
    public JdbcConnection getJdbcConnection()
    {
        if (connectionContext != null)
        {
            return connectionContext.getJdbcConnection();
        }
        return null;
    }

    @Override
    public Connection createConnection() throws SQLException
    {
        if (connectionContext != null)
        {
            return connectionContext.createConnection();
        }
        return null;
    }

    static class TextEditorDocumentParserProxy implements ITextEditorDocumentParser
    {
        ITextEditorDocumentParser currentParser;

        @Override
        public void parse(Reader documentReader)
        {
            if (currentParser != null)
            {
                currentParser.parse(documentReader);
            }
        }

        @Override
        public CompletionResult getCompletionItems(int offset)
        {
            if (currentParser != null)
            {
                return currentParser.getCompletionItems(offset);
            }
            return ITextEditorDocumentParser.super.getCompletionItems(offset);
        }

        @Override
        public LinkAction getLinkAction(int offset)
        {
            if (currentParser != null)
            {
                return currentParser.getLinkAction(offset);
            }
            return ITextEditorDocumentParser.super.getLinkAction(offset);
        }

        @Override
        public List<ParseItem> getParseResult()
        {
            if (currentParser != null)
            {
                return currentParser.getParseResult();
            }
            return ITextEditorDocumentParser.super.getParseResult();
        }

        @Override
        public ToolTipItem getToolTip(int offset)
        {
            if (currentParser != null)
            {
                return currentParser.getToolTip(offset);
            }
            return ITextEditorDocumentParser.super.getToolTip(offset);
        }

        @Override
        public boolean supportsCompletions()
        {
            return true;
        }

        @Override
        public boolean supportsLinkActions()
        {
            return true;
        }

        @Override
        public boolean supportsToolTips()
        {
            return true;
        }
    }
}