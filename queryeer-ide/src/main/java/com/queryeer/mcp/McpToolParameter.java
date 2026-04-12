package com.queryeer.mcp;

import com.queryeer.api.component.Property;

/** A single input parameter for an MCP tool. */
public class McpToolParameter
{
    private String name = "";
    private String description = "";
    private ParameterType type = ParameterType.STRING;

    @Property(
            title = "Name",
            order = 0)
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @Property(
            title = "Description",
            order = 1)
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    @Property(
            title = "Type",
            order = 2)
    public ParameterType getType()
    {
        return type;
    }

    public void setType(ParameterType type)
    {
        this.type = type;
    }

    @Override
    public String toString()
    {
        return name + " (" + type + ")";
    }

    /** Data type of an MCP tool parameter, used for validation and SQL injection mitigation. */
    public enum ParameterType
    {
        STRING("string"),
        INTEGER("integer"),
        NUMBER("number"),
        BOOLEAN("boolean");

        final String jsonSchemaType;

        ParameterType(String jsonSchemaType)
        {
            this.jsonSchemaType = jsonSchemaType;
        }

        public String getJsonSchemaType()
        {
            return jsonSchemaType;
        }
    }
}
