package com.queryeer.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.mcp.McpToolParameter.ParameterType;

/** Round-trip serialization tests for MCP config domain objects */
class McpServerConfigSerializationTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void mcpServerConfig_default_port_3700()
    {
        McpServerConfig cfg = new McpServerConfig();
        assertEquals(3700, cfg.getPort());
        assertTrue(cfg.getTools()
                .isEmpty());
    }

    @Test
    void mcpServerConfig_round_trip() throws Exception
    {
        McpServerConfig cfg = new McpServerConfig();
        cfg.setPort(4000);

        McpTool tool = new McpTool();
        tool.setName("my_tool");
        tool.setDescription("Desc");
        tool.setActive(false);
        tool.setEngineClass("com.example.Engine");
        tool.setQuery("SELECT 1");

        McpToolParameter param = new McpToolParameter();
        param.setName("id");
        param.setDescription("The id");
        param.setType(ParameterType.INTEGER);
        tool.setParameters(List.of(param));

        cfg.setTools(List.of(tool));

        // Serialize via Map (as IConfig does)
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = MAPPER.convertValue(cfg, Map.class);
        McpServerConfig restored = MAPPER.convertValue(raw, McpServerConfig.class);

        assertEquals(4000, restored.getPort());
        assertEquals(1, restored.getTools()
                .size());
        McpTool rt = restored.getTools()
                .get(0);
        assertEquals("my_tool", rt.getName());
        assertEquals("Desc", rt.getDescription());
        assertEquals(false, rt.isActive());
        assertEquals("com.example.Engine", rt.getEngineClass());
        assertEquals("SELECT 1", rt.getQuery());
        assertEquals(1, rt.getParameters()
                .size());
        assertEquals("id", rt.getParameters()
                .get(0)
                .getName());
        assertEquals(ParameterType.INTEGER, rt.getParameters()
                .get(0)
                .getType());
    }

    @Test
    void unknown_fields_in_json_ignored() throws Exception
    {
        String json = "{\"port\":5000,\"unknownField\":\"ignored\",\"tools\":[]}";
        McpServerConfig cfg = MAPPER.readValue(json, McpServerConfig.class);
        assertEquals(5000, cfg.getPort());
        assertTrue(cfg.getTools()
                .isEmpty());
    }

    @Test
    void mcpTool_defaults()
    {
        McpTool tool = new McpTool();
        assertEquals("", tool.getName());
        assertEquals("", tool.getDescription());
        assertTrue(tool.isActive());
        assertEquals("", tool.getEngineClass());
        assertEquals("", tool.getQuery());
        assertNotNull(tool.getParameters());
        assertNotNull(tool.getConnectionConfig());
    }

    @Test
    void mcpToolParameter_defaults()
    {
        McpToolParameter p = new McpToolParameter();
        assertEquals("", p.getName());
        assertEquals("", p.getDescription());
        assertEquals(ParameterType.STRING, p.getType());
    }
}
