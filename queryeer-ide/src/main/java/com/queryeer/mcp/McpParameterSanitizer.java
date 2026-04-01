package com.queryeer.mcp;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.queryeer.mcp.McpToolParameter.ParameterType;

/**
 * Validates MCP tool parameter against it's defined type.
 */
class McpParameterSanitizer
{
    private McpParameterSanitizer()
    {
    }

    /**
     * Validates value for the given parameter type. Throws IllegalArgumentException if the value is invalid for the declared type.
     */
    static Object sanitize(String paramName, String rawValue, ParameterType type)
    {
        if (isBlank(rawValue))
        {
            throw new IllegalArgumentException("Required parameter '" + paramName + "' is missing");
        }

        return switch (type)
        {
            case INTEGER ->
            {
                try
                {
                    yield Long.parseLong(rawValue.trim());
                }
                catch (NumberFormatException e)
                {
                    throw new IllegalArgumentException("Parameter '" + paramName + "' must be an integer, got: " + rawValue);
                }
            }
            case NUMBER ->
            {
                try
                {
                    yield Double.parseDouble(rawValue.trim());
                }
                catch (NumberFormatException e)
                {
                    throw new IllegalArgumentException("Parameter '" + paramName + "' must be a number, got: " + rawValue);
                }
            }
            case BOOLEAN ->
            {
                String trimmed = rawValue.trim()
                        .toLowerCase();
                if ("true".equals(trimmed)
                        || "1".equals(trimmed))
                {
                    yield true;
                }
                else if ("false".equals(trimmed)
                        || "0".equals(trimmed))
                {
                    yield false;
                }
                else
                {
                    throw new IllegalArgumentException("Parameter '" + paramName + "' must be a boolean (true/false/1/0), got: " + rawValue);
                }
            }
            case STRING -> rawValue;
        };
    }
}
