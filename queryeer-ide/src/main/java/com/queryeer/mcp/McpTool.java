package com.queryeer.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A single MCP tool definition that exposes a query as a callable tool. */
public class McpTool
{
    private String name = "";
    private String description = "";
    private boolean active = true;
    private String engineClass = "";
    private Map<String, Object> connectionConfig = new HashMap<>();
    private String query = "";
    private List<McpToolParameter> parameters = new ArrayList<>();

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public boolean isActive()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }

    /** Class name of the IQueryEngine that executes this tool. */
    public String getEngineClass()
    {
        return engineClass;
    }

    public void setEngineClass(String engineClass)
    {
        this.engineClass = engineClass;
    }

    /** Engine-specific connection configuration (e.g. selected JDBC connection, database). */
    public Map<String, Object> getConnectionConfig()
    {
        return connectionConfig;
    }

    public void setConnectionConfig(Map<String, Object> connectionConfig)
    {
        this.connectionConfig = connectionConfig;
    }

    /** Freemarker template query text. Parameters referenced as ${paramName}. */
    public String getQuery()
    {
        return query;
    }

    public void setQuery(String query)
    {
        this.query = query;
    }

    public List<McpToolParameter> getParameters()
    {
        return parameters;
    }

    public void setParameters(List<McpToolParameter> parameters)
    {
        this.parameters = parameters;
    }

    @Override
    public String toString()
    {
        return name + (active ? ""
                : " (inactive)");
    }
}
