package se.kuseman.payloadbuilder.catalog.jdbc.model;

/** Parameter */
public class RoutineParameter
{
    private final String name;
    private final String type;
    private final int maxLength;
    private final int precision;
    private final int scale;
    private final boolean nullable;
    private final boolean output;

    public RoutineParameter(String name, String type, int maxLength, int precision, int scale, boolean nullable, boolean output)
    {
        this.name = name;
        this.type = type;
        this.maxLength = maxLength;
        this.precision = precision;
        this.scale = scale;
        this.nullable = nullable;
        this.output = output;
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

    public boolean isOutput()
    {
        return output;
    }
}
