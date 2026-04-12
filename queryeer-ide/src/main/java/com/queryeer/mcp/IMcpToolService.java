package com.queryeer.mcp;

import java.util.List;
import java.util.Map;

import com.queryeer.api.extensions.IExtension;

/**
 * Internal service for listing and executing MCP tools without going through the HTTP/JSON-RPC layer. Use this when both the caller and the MCP server run in the same JVM.
 */
public interface IMcpToolService extends IExtension
{
    /** Returns all currently active tool definitions. Empty if the server is stopped or no tools are active. */
    List<McpTool> getTools();

    /**
     * Executes the named tool with the supplied arguments and returns the text result. Never throws; returns an error string on failure.
     *
     * @param toolName the tool to execute
     * @param arguments pre-parsed argument map (keys match parameter names)
     */
    String executeTool(String toolName, Map<String, Object> arguments) throws Exception;
}
