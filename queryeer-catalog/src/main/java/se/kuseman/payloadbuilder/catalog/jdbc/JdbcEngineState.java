package se.kuseman.payloadbuilder.catalog.jdbc;

import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.IQueryEngine.IState;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

class JdbcEngineState implements IQueryEngine.IState, IConnectionState
{
    final TextEditorDocumentParserProxy documentParser;
    ConnectionState connectionState;

    JdbcEngineState()
    {
        this(null);
    }

    JdbcEngineState(ConnectionState connectionState)
    {
        this.connectionState = connectionState;
        this.documentParser = new TextEditorDocumentParserProxy();
        resetParser();
    }

    IState cloneState()
    {
        JdbcEngineState newState = new JdbcEngineState();
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

    @Override
    public void close() throws IOException
    {
        if (connectionState != null)
        {
            connectionState.close();
        }
    }

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