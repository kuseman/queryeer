package com.queryeer.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.extensions.engine.IMcpHandler;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.service.ITemplateService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import se.kuseman.payloadbuilder.api.OutputWriter;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

/** Tests for {@link McpHttpHandler} */
class McpHttpHandlerTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpHttpHandler handler;
    private McpServerConfig serverConfig;
    private MockEngine mockEngine;

    @BeforeEach
    void setup()
    {
        mockEngine = new MockEngine();
        serverConfig = new McpServerConfig();
        serverConfig.setPort(3700);

        IConfig config = new MockConfig(List.of(mockEngine));
        handler = new McpHttpHandler(config, serverConfig, new MockTemplateService(), Mockito.mock(IQueryFileProvider.class));
    }

    // ---- initialize ----

    @Test
    void initialize_returns_session_id_and_capabilities() throws IOException
    {
        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        handler.handle(exchange);

        assertEquals(200, exchange.responseCode);
        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertEquals("2.0", response.path("jsonrpc")
                .asText());
        assertNotNull(response.path("result")
                .path("capabilities")
                .path("tools"));
        assertNotNull(response.path("result")
                .path("serverInfo")
                .path("name")
                .asText());
        assertNotNull(exchange.responseHeaders.getFirst("Mcp-Session-Id"));
    }

    // ---- notifications/initialized ----

    @Test
    void initialized_notification_returns_202() throws IOException
    {
        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        handler.handle(exchange);
        assertEquals(202, exchange.responseCode);
    }

    // ---- tools/list ----

    @Test
    void tools_list_returns_only_active_tools() throws IOException
    {
        McpTool activeTool = buildTool("active_tool", true);
        McpTool inactiveTool = buildTool("inactive_tool", false);
        serverConfig.setTools(List.of(activeTool, inactiveTool));
        handler.updateConfig(serverConfig);

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");
        handler.handle(exchange);

        assertEquals(200, exchange.responseCode);
        JsonNode tools = MAPPER.readTree(exchange.responseBody())
                .path("result")
                .path("tools");
        assertEquals(4, tools.size()); // One active tool and 3 internal
        assertEquals("active_tool", tools.get(3)
                .path("name")
                .asText());
    }

    @Test
    void tools_list_includes_json_schema() throws IOException
    {
        McpToolParameter intParam = new McpToolParameter();
        intParam.setName("user_id");
        intParam.setDescription("The user ID");
        intParam.setType(McpToolParameter.ParameterType.INTEGER);

        McpToolParameter numParam = new McpToolParameter();
        numParam.setName("score");
        numParam.setDescription("The score");
        numParam.setType(McpToolParameter.ParameterType.NUMBER);

        McpToolParameter boolParam = new McpToolParameter();
        boolParam.setName("active");
        boolParam.setDescription("Is active");
        boolParam.setType(McpToolParameter.ParameterType.BOOLEAN);

        McpToolParameter strParam = new McpToolParameter();
        strParam.setName("label");
        strParam.setDescription("The label");
        strParam.setType(McpToolParameter.ParameterType.STRING);

        McpTool tool = buildTool("get_user", true);
        tool.setParameters(List.of(intParam, numParam, boolParam, strParam));
        serverConfig.setTools(List.of(tool));
        handler.updateConfig(serverConfig);

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");
        handler.handle(exchange);

        JsonNode schema = MAPPER.readTree(exchange.responseBody())
                .path("result")
                .path("tools")
                .get(3) // 3 internal tools
                .path("inputSchema");
        assertEquals("object", schema.path("type")
                .asText());

        JsonNode props = schema.path("properties");
        assertEquals("integer", props.path("user_id")
                .path("type")
                .asText());
        assertEquals("The user ID", props.path("user_id")
                .path("description")
                .asText());
        assertEquals("number", props.path("score")
                .path("type")
                .asText());
        assertEquals("boolean", props.path("active")
                .path("type")
                .asText());
        assertEquals("string", props.path("label")
                .path("type")
                .asText());

        String required = schema.path("required")
                .toString();
        assertTrue(required.contains("user_id"));
        assertTrue(required.contains("score"));
        assertTrue(required.contains("active"));
        assertTrue(required.contains("label"));
    }

    // ---- tools/call ----

    @Test
    void tools_call_happy_path_returns_json() throws IOException
    {
        McpTool tool = buildTool("my_tool", true);
        tool.setQuery("SELECT 1");
        serverConfig.setTools(List.of(tool));
        handler.updateConfig(serverConfig);

        // Mock engine writes one row
        mockEngine.resultColumns = new String[] { "col" };
        mockEngine.resultRows = List.of(List.of("hello"));

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"my_tool\",\"arguments\":{}}}");
        handler.handle(exchange);

        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertFalse(response.path("result")
                .path("isError")
                .asBoolean());
        String text = response.path("result")
                .path("content")
                .get(0)
                .path("text")
                .asText();
        assertEquals("{\"columns\":[\"col\"],\"rows\":[[\"hello\"]]}", text);
    }

    @Test
    void tools_call_unknown_tool_returns_error() throws IOException
    {
        serverConfig.setTools(List.of());
        handler.updateConfig(serverConfig);

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"no_such_tool\",\"arguments\":{}}}");
        handler.handle(exchange);

        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertTrue(response.path("result")
                .path("isError")
                .asBoolean());
        assertTrue(response.path("result")
                .path("content")
                .get(0)
                .path("text")
                .asText()
                .contains("Unknown tool"));
    }

    @Test
    void tools_call_inactive_tool_returns_error() throws IOException
    {
        McpTool tool = buildTool("inactive", false);
        serverConfig.setTools(List.of(tool));
        handler.updateConfig(serverConfig);

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"inactive\",\"arguments\":{}}}");
        handler.handle(exchange);

        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertTrue(response.path("result")
                .path("isError")
                .asBoolean());
    }

    /** Covers line 240: parameters loop — all four parameter types are extracted, sanitized, and forwarded to the engine. */
    @Test
    void tools_call_with_parameters_extracts_and_forwards_args() throws IOException
    {
        McpToolParameter intParam = new McpToolParameter();
        intParam.setName("user_id");
        intParam.setType(McpToolParameter.ParameterType.INTEGER);

        McpToolParameter numParam = new McpToolParameter();
        numParam.setName("score");
        numParam.setType(McpToolParameter.ParameterType.NUMBER);

        McpToolParameter boolParam = new McpToolParameter();
        boolParam.setName("active");
        boolParam.setType(McpToolParameter.ParameterType.BOOLEAN);

        McpToolParameter strParam = new McpToolParameter();
        strParam.setName("label");
        strParam.setType(McpToolParameter.ParameterType.STRING);

        McpTool tool = buildTool("param_tool", true);
        tool.setQuery("SELECT 1");
        tool.setParameters(List.of(intParam, numParam, boolParam, strParam));
        serverConfig.setTools(List.of(tool));
        handler.updateConfig(serverConfig);

        mockEngine.resultColumns = new String[] { "col" };
        mockEngine.resultRows = List.of(List.of("ok"));

        MockExchange exchange = post(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"param_tool\",\"arguments\":" + "{\"user_id\":42,\"score\":3.14,\"active\":true,\"label\":\"hello\"}}}");
        handler.handle(exchange);

        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertFalse(response.path("result")
                .path("isError")
                .asBoolean());
        assertNotNull(mockEngine.capturedArguments);
        assertEquals(42L, mockEngine.capturedArguments.get("user_id"));
        assertEquals(3.14, (Double) mockEngine.capturedArguments.get("score"), 1e-9);
        assertEquals(Boolean.TRUE, mockEngine.capturedArguments.get("active"));
        assertEquals("hello", mockEngine.capturedArguments.get("label"));
    }

    /** Covers line 252: engine class not registered → error response. */
    @Test
    void tools_call_unknown_engine_returns_error() throws IOException
    {
        McpTool tool = new McpTool();
        tool.setName("orphan_tool");
        tool.setActive(true);
        tool.setEngineClass("com.example.NonExistentEngine");
        serverConfig.setTools(List.of(tool));
        handler.updateConfig(serverConfig);

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"orphan_tool\",\"arguments\":{}}}");
        handler.handle(exchange);

        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertTrue(response.path("result")
                .path("isError")
                .asBoolean());
        assertTrue(response.path("result")
                .path("content")
                .get(0)
                .path("text")
                .asText()
                .contains("Query engine not found"));
    }

    /** Covers line 258: engine found but getMcpHandler() returns null → error response. */
    @Test
    void tools_call_engine_without_mcp_support_returns_error() throws IOException
    {
        MockEngineNoMcp noMcpEngine = new MockEngineNoMcp();
        McpHttpHandler localHandler = new McpHttpHandler(new MockConfig(List.of(noMcpEngine)), serverConfig, new MockTemplateService(), Mockito.mock(IQueryFileProvider.class));

        McpTool tool = new McpTool();
        tool.setName("no_mcp_tool");
        tool.setActive(true);
        tool.setEngineClass(MockEngineNoMcp.class.getName());
        McpServerConfig localConfig = new McpServerConfig();
        localConfig.setPort(3701);
        localConfig.setTools(List.of(tool));
        localHandler.updateConfig(localConfig);

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"no_mcp_tool\",\"arguments\":{}}}");
        localHandler.handle(exchange);

        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertTrue(response.path("result")
                .path("isError")
                .asBoolean());
        assertTrue(response.path("result")
                .path("content")
                .get(0)
                .path("text")
                .asText()
                .contains("does not support MCP"));
    }

    @Test
    void tools_call_engine_exception_returns_error() throws IOException
    {
        McpTool tool = buildTool("bad_tool", true);
        tool.setQuery("BOOM");
        serverConfig.setTools(List.of(tool));
        handler.updateConfig(serverConfig);

        mockEngine.throwOnExecute = new RuntimeException("DB is down");

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"bad_tool\",\"arguments\":{}}}");
        handler.handle(exchange);

        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertTrue(response.path("result")
                .path("isError")
                .asBoolean());
        assertTrue(response.path("result")
                .path("content")
                .get(0)
                .path("text")
                .asText()
                .contains("DB is down"));
    }

    // ---- table output tools (integration through HTTP handler) ----

    @Test
    void tools_call_table_meta_returns_json_via_http() throws IOException
    {
        McpTableOutputHandlerTest.TableData table = new McpTableOutputHandlerTest.TableData(List.of("id", "name"), List.of(Integer.class, String.class), new Object[7][2]);

        IQueryFileProvider tableProvider = buildProviderWithTable(1, table);
        McpHttpHandler tableHandler = new McpHttpHandler(new MockConfig(List.of()), serverConfig, new MockTemplateService(), tableProvider);

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"," + "\"params\":{\"name\":\"__resultset_meta\",\"arguments\":{}}}");
        tableHandler.handle(exchange);

        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertFalse(response.path("result")
                .path("isError")
                .asBoolean());

        String text = response.path("result")
                .path("content")
                .get(0)
                .path("text")
                .asText();
        JsonNode tableData = MAPPER.readTree(text);
        assertEquals(1, tableData.path("resultsetCount")
                .asInt());
        assertEquals(7, tableData.path("resultsets")
                .get(0)
                .path("rowCount")
                .asInt());
    }

    @Test
    void tools_call_table_get_rows_returns_json_via_http() throws IOException
    {
        Object[][] data = { { 1, "Alice" }, { 2, "Bob" }, { 3, "Carol" } };
        McpTableOutputHandlerTest.TableData table = new McpTableOutputHandlerTest.TableData(List.of("id", "name"), List.of(Integer.class, String.class), data);

        IQueryFileProvider tableProvider = buildProviderWithTable(2, table);
        McpHttpHandler tableHandler = new McpHttpHandler(new MockConfig(List.of()), serverConfig, new MockTemplateService(), tableProvider);

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"," + "\"params\":{\"name\":\"__resultset_get_rows\","
                                     + "\"arguments\":{\"resultsetIndex\":0,\"columns\":\"*\",\"offset\":0,\"limit\":3}}}");
        tableHandler.handle(exchange);

        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertFalse(response.path("result")
                .path("isError")
                .asBoolean());

        String text = response.path("result")
                .path("content")
                .get(0)
                .path("text")
                .asText();
        JsonNode tableData = MAPPER.readTree(text);
        assertEquals(3, tableData.path("rows")
                .size());
    }

    @Test
    void tools_call_table_meta_unknown_file_returns_error_via_http() throws IOException
    {
        IQueryFileProvider emptyProvider = Mockito.mock(IQueryFileProvider.class);
        McpHttpHandler tableHandler = new McpHttpHandler(new MockConfig(List.of()), serverConfig, new MockTemplateService(), emptyProvider);

        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"," + "\"params\":{\"name\":\"__table_meta\",\"arguments\":{}}}");
        tableHandler.handle(exchange);

        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertTrue(response.path("result")
                .path("isError")
                .asBoolean());
    }

    private IQueryFileProvider buildProviderWithTable(int fileId, ITableOutputComponent.Table table)
    {
        ITableOutputComponent comp = Mockito.mock(ITableOutputComponent.class);
        Mockito.doReturn(List.of(table))
                .when(comp)
                .getTables();

        IQueryFile queryFile = Mockito.mock(IQueryFile.class);
        Mockito.when(queryFile.getOutputComponent(ITableOutputComponent.class))
                .thenReturn(comp);

        IQueryFileProvider provider = Mockito.mock(IQueryFileProvider.class);
        Mockito.when(provider.getCurrentFile())
                .thenReturn(queryFile);
        return provider;
    }

    // ---- unknown method ----

    @Test
    void unknown_method_returns_method_not_found_error() throws IOException
    {
        MockExchange exchange = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"unknown/method\",\"params\":{}}");
        handler.handle(exchange);

        assertEquals(200, exchange.responseCode);
        JsonNode response = MAPPER.readTree(exchange.responseBody());
        assertEquals(-32601, response.path("error")
                .path("code")
                .asInt());
    }

    // ---- malformed JSON ----

    @Test
    void malformed_json_returns_500() throws IOException
    {
        MockExchange exchange = post("not-valid-json{{{");
        handler.handle(exchange);

        assertTrue(exchange.responseCode >= 500
                || MAPPER.readTree(exchange.responseBody())
                        .has("error"));
    }

    // ---- non-POST ----

    @Test
    void get_request_returns_405() throws IOException
    {
        MockExchange exchange = new MockExchange("GET", "{}");
        handler.handle(exchange);
        assertEquals(405, exchange.responseCode);
    }

    // ---- helpers ----

    private McpTool buildTool(String name, boolean active)
    {
        McpTool tool = new McpTool();
        tool.setName(name);
        tool.setActive(active);
        tool.setEngineClass(MockEngine.class.getName());
        return tool;
    }

    private MockExchange post(String body)
    {
        return new MockExchange("POST", body);
    }

    // ---- Mock infrastructure ----

    static class MockTemplateService implements ITemplateService
    {
        private static final Configuration FTL_CONFIG;

        static
        {
            FTL_CONFIG = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
            FTL_CONFIG.setDefaultEncoding("UTF-8");
            FTL_CONFIG.setLocale(Locale.US);
            FTL_CONFIG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        }

        @Override
        public String process(String name, String template, Map<String, Object> model, boolean throwErrors)
        {
            try
            {
                Template ftl = new Template(name, new StringReader(template), FTL_CONFIG);
                StringWriter out = new StringWriter();
                ftl.process(model, out);
                return out.toString();
            }
            catch (Exception e)
            {
                if (throwErrors)
                {
                    throw e instanceof RuntimeException re ? re
                            : new RuntimeException(e);
                }
                return "";
            }
        }
    }

    static class MockConfig implements IConfig
    {
        private final List<IQueryEngine> engines;

        MockConfig(List<IQueryEngine> engines)
        {
            this.engines = engines;
        }

        @Override
        public Map<String, Object> loadExtensionConfig(String name)
        {
            return Map.of();
        }

        @Override
        public void saveExtensionConfig(String name, Map<String, ?> config)
        {
        }

        @Override
        public File getConfigFileName(String name)
        {
            return null;
        }

        @Override
        public List<IQueryEngine> getQueryEngines()
        {
            return engines;
        }
    }

    static class MockEngineNoMcp implements IQueryEngine
    {
        @Override
        public IEditor createEditor(IState state, String filename)
        {
            return null;
        }

        @Override
        public void execute(IQueryFile queryFile, OutputWriter writer, Object query) throws Exception
        {
        }

        @Override
        public IMcpHandler getMcpHandler()
        {
            return null;
        }

        @Override
        public void abortQuery(IQueryFile queryFile)
        {
        }

        @Override
        public java.awt.Component getQuickPropertiesComponent()
        {
            return null;
        }

        @Override
        public String getTitle()
        {
            return "No-MCP Engine";
        }

        @Override
        public String getDefaultFileExtension()
        {
            return "sql";
        }
    }

    static class MockEngine implements IQueryEngine
    {
        String[] resultColumns = new String[0];
        List<List<Object>> resultRows = List.of();
        Exception throwOnExecute;
        Map<String, Object> capturedArguments;

        @Override
        public IEditor createEditor(IState state, String filename)
        {
            return null;
        }

        @Override
        public void execute(IQueryFile queryFile, OutputWriter writer, Object query) throws Exception
        {
        }

        @Override
        public IMcpHandler getMcpHandler()
        {
            return new IMcpHandler()
            {
                @Override
                public McpResult execute(Map<String, Object> mcpConnectionConfig, String query, Map<String, Object> parameters) throws Exception
                {
                    if (throwOnExecute != null)
                    {
                        throw throwOnExecute;
                    }
                    capturedArguments = parameters;

                    return new McpResult(List.of(resultColumns), resultRows.stream()
                            .map(List::toArray)
                            .toList());
                }
            };
        }

        @Override
        public void abortQuery(IQueryFile queryFile)
        {
        }

        @Override
        public java.awt.Component getQuickPropertiesComponent()
        {
            return null;
        }

        @Override
        public String getTitle()
        {
            return "Mock Engine";
        }

        @Override
        public String getDefaultFileExtension()
        {
            return "sql";
        }
    }

    static class MockExchange extends HttpExchange
    {
        private final String method;
        private final byte[] requestBody;
        final Headers responseHeaders = new Headers();
        final ByteArrayOutputStream responseBodyStream = new ByteArrayOutputStream();
        int responseCode = -1;

        MockExchange(String method, String body)
        {
            this.method = method;
            this.requestBody = body.getBytes(StandardCharsets.UTF_8);
        }

        String responseBody()
        {
            return responseBodyStream.toString(StandardCharsets.UTF_8);
        }

        @Override
        public Headers getRequestHeaders()
        {
            return new Headers();
        }

        @Override
        public Headers getResponseHeaders()
        {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI()
        {
            return URI.create("/mcp");
        }

        @Override
        public String getRequestMethod()
        {
            return method;
        }

        @Override
        public HttpContext getHttpContext()
        {
            return null;
        }

        @Override
        public void close()
        {
        }

        @Override
        public java.io.InputStream getRequestBody()
        {
            return new ByteArrayInputStream(requestBody);
        }

        @Override
        public java.io.OutputStream getResponseBody()
        {
            return responseBodyStream;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) throws IOException
        {
            this.responseCode = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress()
        {
            return null;
        }

        @Override
        public int getResponseCode()
        {
            return responseCode;
        }

        @Override
        public InetSocketAddress getLocalAddress()
        {
            return null;
        }

        @Override
        public String getProtocol()
        {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name)
        {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value)
        {
        }

        @Override
        public void setStreams(java.io.InputStream i, java.io.OutputStream o)
        {
        }

        @Override
        public HttpPrincipal getPrincipal()
        {
            return null;
        }
    }
}
