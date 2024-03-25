package se.kuseman.payloadbuilder.catalog.jdbc.model;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

/** Base class for SQL objects */
public class ObjectName
{
    private final String catalog;
    private final String schema;
    private final String name;

    public ObjectName(String catalog, String schema, String name)
    {
        this.catalog = catalog;
        this.schema = schema;
        this.name = requireNonNull(name, "name");
    }

    public String getCatalog()
    {
        return catalog;
    }

    public String getSchema()
    {
        return schema;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public int hashCode()
    {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
        {
            return true;
        }
        else if (obj == null)
        {
            return false;
        }
        else if (getClass() == obj.getClass())
        {
            ObjectName that = (ObjectName) obj;

            return name.equals(that.name)
                    && Objects.equals(schema, that.schema)
                    && Objects.equals(catalog, that.catalog);
        }
        return false;
    }

    @Override
    public String toString()
    {
        return "%s.%s.%s".formatted(catalog, schema, name);
    }
}
