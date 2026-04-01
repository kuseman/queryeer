package com.queryeer.api.extensions.engine;

import java.awt.Component;
import java.util.Map;
import java.util.function.Consumer;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Definition of handler for MCP server. This handler executes queries that targets the owning {@link IQueryEngine}. etc. */
public interface IMcpHandler
{
    /**
     * Returns an engine-specific UI component for configuring MCP tool connection settings. The config map is read/written by the component. Returns null if the engine has no connection concept (e.g.
     * filesystem engines).
     */
    default Component getMcpConnectionComponent(Map<String, Object> config, Consumer<Boolean> dirtyConsumer)
    {
        return null;
    }

    /**
     * Returns an engine-specific hint describing how to safely inject parameter values into a query (e.g. ":paramName" for JDBC, "@paramName" for Payloadbuilder). Returns null if not applicable.
     */
    default String getParameterSyntaxHint()
    {
        return null;
    }

    /**
     * Executes provided query with parameters to provided output writer. NOTE! Query is unprocessed regarding param place holders. It's up to the query engine to parse/replace.
     */
    void execute(Map<String, Object> mcpConnectionConfig, String query, Map<String, Object> parameters, OutputWriter outputWriter) throws Exception;
}
