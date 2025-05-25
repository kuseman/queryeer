package se.kuseman.payloadbuilder.catalog.jdbc.model;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.Strings.CI;

/** Column */
public class Column
{
    private final String name;
    private final String type;
    private final int maxLength;
    private final int precision;
    private final int scale;
    private final boolean nullable;
    private final String primaryKeyName;

    public Column(String name, String type, int maxLength, int precision, int scale, boolean nullable, String primaryKeyName)
    {
        this.name = requireNonNull(name, "name");
        this.type = requireNonNull(type, "type");
        this.maxLength = maxLength;
        this.precision = precision;
        this.scale = scale;
        this.nullable = nullable;
        this.primaryKeyName = primaryKeyName;
    }

    public String getName()
    {
        return name;
    }

    public String getType()
    {
        return type;
    }

    public int getMaxLength()
    {
        return maxLength;
    }

    public int getPrecision()
    {
        return precision;
    }

    public int getScale()
    {
        return scale;
    }

    public boolean isNullable()
    {
        return nullable;
    }

    public String getPrimaryKeyName()
    {
        return primaryKeyName;
    }

    /** Return type definition for this column */
    public String getDefinition()
    {
        return getDefinition(type, maxLength, precision, scale, nullable);
    }

    /** Construct definition string for a column with provided parts */
    public static String getDefinition(String type, int maxLength, int precision, int scale, boolean nullable)
    {
        String typeStr = type;
        if (CI.contains(type, "char"))
        {
            typeStr = "%s (%s)".formatted(type, maxLength < 0 ? "max"
                    : maxLength);
        }
        else if (CI.contains(type, "date")
                || CI.contains(type, "time")
                || "int".equalsIgnoreCase(type))
        {
            typeStr = type;
        }
        else if (scale > 0)
        {
            typeStr = "%s (%d, %d)".formatted(type, precision, scale);
        }
        else
        {
            typeStr = "%s (%s)".formatted(type, maxLength);
        }

        return "%s%s".formatted(typeStr, nullable ? ""
                : " not null");
    }
}
