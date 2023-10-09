package se.kuseman.payloadbuilder.catalog.jdbc.model;

import static java.util.Objects.requireNonNull;

import java.util.List;

/** Model of a foreign key */
public class ForeignKey
{
    private final ObjectName objectName;
    private final List<ForeignKeyColumn> columns;

    public ForeignKey(ObjectName objectName, List<ForeignKeyColumn> columns)
    {
        this.objectName = requireNonNull(objectName, "objectName");
        this.columns = requireNonNull(columns, "columns");
    }

    public ObjectName getObjectName()
    {
        return objectName;
    }

    public List<ForeignKeyColumn> getColumns()
    {
        return columns;
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
        else if (obj instanceof ForeignKey that)
        {
            return objectName.equals(that.objectName)
                    && columns.equals(that.columns);
        }
        return false;
    }
}
