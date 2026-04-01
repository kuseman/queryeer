package com.queryeer.mcp;

import java.util.ArrayList;
import java.util.List;

/** Configuration for the embedded MCP server. Persisted via IConfig. */
class McpServerConfig
{
    private int port = 3700;
    private boolean enabled = true;
    private List<McpTool> tools = new ArrayList<>();

    public int getPort()
    {
        return port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public List<McpTool> getTools()
    {
        return tools;
    }

    public void setTools(List<McpTool> tools)
    {
        this.tools = tools;
    }
}
