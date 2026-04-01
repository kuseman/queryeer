package com.queryeer.mcp;

import com.queryeer.api.event.Event;

/** Published on the event bus whenever the MCP server starts or stops. */
public class McpServerStatusEvent extends Event
{
    private final boolean running;
    private final int port;

    McpServerStatusEvent(boolean running, int port)
    {
        this.running = running;
        this.port = port;
    }

    public boolean isRunning()
    {
        return running;
    }

    public int getPort()
    {
        return port;
    }
}
