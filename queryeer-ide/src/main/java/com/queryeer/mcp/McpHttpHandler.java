package com.queryeer.mcp;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.queryeer.api.extensions.engine.IMcpHandler;
import com.queryeer.api.extensions.engine.IMcpHandler.McpResult;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.service.ITemplateService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handles MCP (Model Context Protocol) JSON-RPC 2.0 requests over HTTP POST. Implements the Streamable HTTP transport with plain JSON responses (no SSE needed for synchronous tool execution).
 *
 * <p>
 * Supported JSON-RPC methods:
 * <ul>
 * <li>initialize — returns server capabilities and a session ID</li>
 * <li>notifications/initialized — acknowledges client ready (202)</li>
 * <li>tools/list — returns all active tools with their JSON Schema</li>
 * <li>tools/call — executes a tool and returns the result as a markdown table</li>
 * </ul>
 */
class McpHttpHandler implements HttpHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(McpHttpHandler.class);
    static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String MCP_SESSION_HEADER = "Mcp-Session-Id";
    private static final String SERVER_NAME = "Queryeer MCP Server";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final IConfig config;
    private final ITemplateService templateService;
    private final McpTableOutputHandler tableOutputHandler;
    private volatile McpServerConfig serverConfig;
    private final Map<String, Boolean> sessions = new ConcurrentHashMap<>();

    McpHttpHandler(IConfig config, McpServerConfig serverConfig, ITemplateService templateService, IQueryFileProvider queryFileProvider)
    {
        this.config = requireNonNull(config, "config");
        this.serverConfig = requireNonNull(serverConfig, "serverConfig");
        this.templateService = requireNonNull(templateService, "templateService");
        this.tableOutputHandler = new McpTableOutputHandler(queryFileProvider);
    }

    void updateConfig(McpServerConfig serverConfig)
    {
        this.serverConfig = requireNonNull(serverConfig, "serverConfig");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException
    {
        try
        {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
            {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            byte[] body = exchange.getRequestBody()
                    .readAllBytes();
            JsonNode request = MAPPER.readTree(body);

            String method = request.path("method")
                    .asText("");
            JsonNode idNode = request.path("id");
            JsonNode params = request.path("params");

            // notifications have no id and don't need a response body
            boolean isNotification = idNode.isMissingNode()
                    || idNode.isNull();

            String response = switch (method)
            {
                case "initialize" -> handleInitialize(exchange, idNode, params);
                case "notifications/initialized" ->
                {
                    exchange.sendResponseHeaders(202, -1);
                    yield null;
                }
                case "tools/list" -> handleToolsList(idNode);
                case "tools/call" -> handleToolsCall(idNode, params);
                default ->
                {
                    if (isNotification)
                    {
                        exchange.sendResponseHeaders(202, -1);
                        yield null;
                    }
                    yield buildError(idNode, -32601, "Method not found: " + method);
                }
            };

            if (response != null)
            {
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders()
                        .set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody())
                {
                    os.write(responseBytes);
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error handling MCP request", e);
            try
            {
                String error = buildError(null, -32603, "Internal error: " + e.getMessage());
                byte[] responseBytes = error.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders()
                        .set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(500, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody())
                {
                    os.write(responseBytes);
                }
            }
            catch (IOException ignored)
            {
            }
        }
    }

    private String handleInitialize(HttpExchange exchange, JsonNode idNode, JsonNode params)
    {
        String sessionId = UUID.randomUUID()
                .toString();
        sessions.put(sessionId, Boolean.TRUE);
        exchange.getResponseHeaders()
                .set(MCP_SESSION_HEADER, sessionId);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode serverInfo = MAPPER.createObjectNode();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.set("serverInfo", serverInfo);

        ObjectNode capabilities = MAPPER.createObjectNode();
        ObjectNode toolsCap = MAPPER.createObjectNode();
        toolsCap.put("listChanged", false);
        capabilities.set("tools", toolsCap);
        result.set("capabilities", capabilities);

        return buildResult(idNode, result);
    }

    private String handleToolsList(JsonNode idNode)
    {
        ArrayNode toolsArray = MAPPER.createArrayNode();

        for (McpTool tool : getActiveTools())
        {
            ObjectNode toolNode = MAPPER.createObjectNode();
            toolNode.put("name", tool.getName());
            toolNode.put("description", tool.getDescription());

            ObjectNode inputSchema = MAPPER.createObjectNode();
            inputSchema.put("type", "object");
            ObjectNode properties = MAPPER.createObjectNode();
            ArrayNode required = MAPPER.createArrayNode();

            for (McpToolParameter param : tool.getParameters())
            {
                ObjectNode propNode = MAPPER.createObjectNode();
                propNode.put("type", param.getType().jsonSchemaType);
                propNode.put("description", param.getDescription());
                properties.set(param.getName(), propNode);
                required.add(param.getName());
            }

            inputSchema.set("properties", properties);
            inputSchema.set("required", required);
            toolNode.set("inputSchema", inputSchema);

            toolsArray.add(toolNode);
        }

        ObjectNode result = MAPPER.createObjectNode();
        result.set("tools", toolsArray);
        return buildResult(idNode, result);
    }

    private String handleToolsCall(JsonNode idNode, JsonNode params)
    {
        String toolName = params.path("name")
                .asText("");
        JsonNode argumentsNode = params.path("arguments");

        McpTool tool = getActiveTools().stream()
                .filter(t -> t.getName()
                        .equals(toolName))
                .findFirst()
                .orElse(null);

        if (tool == null)
        {
            return buildToolError(idNode, "Unknown tool: " + toolName);
        }

        // Extract raw string values for each declared parameter
        Map<String, Object> arguments = new HashMap<>();
        if (argumentsNode.isObject())
        {
            for (McpToolParameter param : tool.getParameters())
            {
                String name = param.getName();
                JsonNode value = argumentsNode.findValue(name);
                arguments.put(name, value != null ? value.asText()
                        : null);
            }
        }

        String result;
        try
        {
            result = executeToolDirect(toolName, arguments);
        }
        catch (Exception e)
        {
            Throwable t = e;
            if (t.getClass() == RuntimeException.class)
            {
                t = e.getCause() != null ? e.getCause()
                        : t;
            }
            return buildToolError(idNode, "Error executing tool '" + toolName + "': " + t.getMessage());
        }
        return buildToolResult(idNode, result, false);
    }

    /** Returns all active tools from both built-in (table output) and user-defined sources. */
    List<McpTool> getActiveTools()
    {
        List<McpTool> tools = new ArrayList<>();
        tools.addAll(tableOutputHandler.getTableOutputTools());
        tools.addAll(serverConfig.getTools());
        tools.removeIf(t -> !t.isActive());
        return tools;
    }

    /**
     * Executes a tool by name, sanitizing and type-coercing the arguments first. Never throws; returns an error string on failure.
     */
    String executeToolDirect(String toolName, Map<String, Object> arguments) throws Exception
    {
        McpTool tool = getActiveTools().stream()
                .filter(t -> t.getName()
                        .equals(toolName))
                .findFirst()
                .orElse(null);

        if (tool == null)
        {
            throw new IllegalArgumentException("Error: Unknown tool: " + toolName);
        }

        // Sanitize and type-coerce all declared parameters
        Map<String, Object> sanitized = new HashMap<>();
        for (McpToolParameter param : tool.getParameters())
        {
            Object raw = arguments.get(param.getName());
            String rawStr = raw != null ? String.valueOf(raw)
                    : null;
            sanitized.put(param.getName(), McpParameterSanitizer.sanitize(param.getName(), rawStr, param.getType()));
        }
        String result = tableOutputHandler.handleToolCall(toolName, sanitized);
        if (result == null)
        {
            String renderedQuery = templateService.process("mcp-tool-" + tool.getName(), tool.getQuery(), sanitized, true);
            IQueryEngine engine = findEngine(tool.getEngineClass());
            if (engine == null)
            {
                throw new IllegalArgumentException("Query engine not found: " + tool.getEngineClass());
            }
            IMcpHandler mcpHandler = engine.getMcpHandler();
            if (mcpHandler == null)
            {
                throw new IllegalArgumentException("Query engine does not support MCP: " + tool.getEngineClass());
            }
            McpResult mcpResult = mcpHandler.execute(tool.getConnectionConfig(), renderedQuery, sanitized);
            result = MAPPER.writeValueAsString(mcpResult);
        }
        return result;
    }

    private IQueryEngine findEngine(String engineClass)
    {
        List<IQueryEngine> engines = config.getQueryEngines();
        if (engines == null)
        {
            return null;
        }
        return engines.stream()
                .filter(e -> e.getClass()
                        .getName()
                        .equals(engineClass))
                .findFirst()
                .orElse(null);
    }

    private String buildResult(JsonNode id, ObjectNode result)
    {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null)
        {
            response.set("id", id);
        }
        else
        {
            response.putNull("id");
        }
        response.set("result", result);
        return response.toString();
    }

    private String buildError(JsonNode id, int code, String message)
    {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null
                && !id.isMissingNode())
        {
            response.set("id", id);
        }
        else
        {
            response.putNull("id");
        }
        ObjectNode error = MAPPER.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        return response.toString();
    }

    private String buildToolResult(JsonNode idNode, String text, boolean isError)
    {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("isError", isError);
        ArrayNode content = MAPPER.createArrayNode();
        ObjectNode textContent = MAPPER.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", text);
        content.add(textContent);
        result.set("content", content);
        return buildResult(idNode, result);
    }

    private String buildToolError(JsonNode idNode, String message)
    {
        return buildToolResult(idNode, message, true);
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException
    {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(bytes);
        }
    }
}
