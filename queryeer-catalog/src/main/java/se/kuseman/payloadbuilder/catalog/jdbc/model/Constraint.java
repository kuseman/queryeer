package se.kuseman.payloadbuilder.catalog.jdbc.model;

import static java.util.Objects.requireNonNull;

/** Model of a constraint */
public class Constraint
{
    private final ObjectName objectName;
    private final String name;
    private final Type type;
    private final String columnName;
    private final String definition;

    public Constraint(ObjectName objectName, String name, Type type, String columnName, String definition)
    {
        this.objectName = requireNonNull(objectName, "objectName");
        this.name = requireNonNull(name, "name");
        this.type = requireNonNull(type, "type");
        this.columnName = requireNonNull(columnName, "columnName");
        this.definition = requireNonNull(definition, "definition");
    }

    public ObjectName getObjectName()
    {
        return objectName;
    }

    public String getName()
    {
        return name;
    }

    public Type getType()
    {
        return type;
    }

    public String getColumnName()
    {
        return columnName;
    }

    public String getDefinition()
    {
        return definition;
    }

    @Override
    public int hashCode()
    {
        return objectName.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
        {
            return false;
        }
        else if (obj == this)
        {
            return true;
        }
        else if (obj instanceof Constraint that)
        {
            return objectName.equals(that.objectName)
                    && name.equals(that.name)
                    && type.equals(that.type)
                    && columnName.equals(that.columnName)
                    && definition.equals(that.definition);
        }
        return false;
    }

    /** Type of contraint */
    public enum Type
    {
        CHECK,
        DEFAULT
    }
}
