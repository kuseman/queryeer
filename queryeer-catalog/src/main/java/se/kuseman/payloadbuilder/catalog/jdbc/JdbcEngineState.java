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

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

class JdbcEngineState implements IQueryEngine.IState, IConnectionState
{
    private final IQueryEngine queryEngine;

    private List<Runnable> changeListeners;
    final TextEditorDocumentParserProxy documentParser;
    ConnectionState connectionState;

    boolean includeQueryPlan;
    boolean estimateQueryPlan;

    JdbcEngineState(IQueryEngine queeryEngine)
    {
        this(queeryEngine, null);
    }

    JdbcEngineState(IQueryEngine queryEngine, ConnectionState connectionState)
    {
        this.queryEngine = requireNonNull(queryEngine);
        this.connectionState = connectionState;
        this.documentParser = new TextEditorDocumentParserProxy();

        resetParser();
    }

    IState cloneState()
    {
        JdbcEngineState newState = new JdbcEngineState(queryEngine);
        if (connectionState != null)
        {
            newState.setConnectionState(connectionState.cloneState());
        }
        return newState;
    }

    void setConnectionState(ConnectionState connectionState)
    {
        this.connectionState = connectionState;
        documentParser.currentParser = null;
        resetParser();

        if (changeListeners != null)
        {
            for (Runnable r : changeListeners)
            {
                r.run();
            }
        }
    }

    void resetParser()
    {
        // Switch parser if we switched state
        if (connectionState != null)
        {
            documentParser.currentParser = connectionState.getJdbcDatabase()
                    .getParser(this);

        }
    }

    void addChangeListener(Runnable r)
    {
        if (changeListeners == null)
        {
            changeListeners = new ArrayList<>();
        }
        changeListeners.add(r);
    }

    void removeChangeListener(Runnable r)
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
            url = connectionState != null ? Objects.toString(connectionState.getJdbcConnection()
                    .getJdbcURL(), "")
                    : "";
            database = connectionState != null ? connectionState.getDatabase()
                    : "";
        }

        return IConnectionState.getMetaParameters(url, database);
    }

    @Override
    public void close() throws IOException
    {
        if (connectionState != null)
        {
            connectionState.close();
        }
    }

    // IState

    @Override
    public String getDatabase()
    {
        if (connectionState != null)
        {
            return connectionState.getDatabase();
        }
        return null;
    }

    @Override
    public JdbcDatabase getJdbcDatabase()
    {
        if (connectionState != null)
        {
            return connectionState.getJdbcDatabase();
        }
        return null;
    }

    @Override
    public JdbcConnection getJdbcConnection()
    {
        if (connectionState != null)
        {
            return connectionState.getJdbcConnection();
        }
        return null;
    }

    @Override
    public Connection createConnection() throws SQLException
    {
        if (connectionState != null)
        {
            return connectionState.createConnection();
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