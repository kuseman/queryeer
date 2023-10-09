package se.kuseman.payloadbuilder.catalog.jdbc;

import java.util.Map;
import java.util.Objects;

import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.editor.ITextEditorKit;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.extensions.engine.IQueryEngine;

/** Text editor kit for jdbc query engines */
class JdbcTextEditorKit implements ITextEditorKit
{
    private final JdbcQueryEngine jdbcQueryEngine;
    private JdbcEngineState state;

    JdbcTextEditorKit(JdbcQueryEngine jdbcQueryEngine, JdbcEngineState state)
    {
        this.jdbcQueryEngine = jdbcQueryEngine;
        this.state = state;
    }

    @Override
    public String getSyntaxMimeType()
    {
        return JdbcQueryEngine.TEXT_SQL;
    }

    @Override
    public IQueryEngine getQueryEngine()
    {
        return this.jdbcQueryEngine;
    }

    @Override
    public ITextEditorDocumentParser getDocumentParser()
    {
        return state.documentParser;
    }

    @Override
    public Map<String, Object> getQueryShortcutRuleParameters()
    {
        if (state.connectionState == null)
        {
            return Map.of("database", "", "url", "");
        }
        return Map.of("database", Objects.toString(state.connectionState.getDatabase(), ""), "url", Objects.toString(state.connectionState.getJdbcConnection()
                .getJdbcURL(), ""));
    }

    @Override
    public ExecuteQueryEvent getQueryShortcutQueryEvent(String query, OutputType outputType)
    {
        if (state.connectionState == null)
        {
            return null;
        }

        return new ExecuteQueryEvent(outputType, new ExecuteQueryContext(query));
    }
}