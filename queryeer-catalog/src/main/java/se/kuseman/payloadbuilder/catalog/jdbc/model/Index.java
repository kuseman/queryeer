package se.kuseman.payloadbuilder.catalog.jdbc.model;

import static java.util.Objects.requireNonNull;

import java.util.List;

/** Model of an index */
public class Index
{
    private final ObjectName objectName;
    private final String indexName;
    private final boolean unique;
    private final List<IndexColumn> columns;

    public Index(ObjectName objectName, String indexName, boolean unique, List<IndexColumn> columns)
    {
        this.objectName = requireNonNull(objectName, "objectName");
        this.indexName = requireNonNull(indexName, "indexName");
        this.unique = unique;
        this.columns = requireNonNull(columns, "columns");
    }

    public ObjectName getObjectName()
    {
        return objectName;
    }

    public String getIndexName()
    {
        return indexName;
    }

    public boolean isUnique()
    {
        return unique;
    }

    public List<IndexColumn> getColumns()
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
        if (obj == this)
        {
            return true;
        }
        else if (obj == null)
        {
            return false;
        }
        else if (obj instanceof Index that)
        {
            return objectName.equals(that.objectName)
                    && indexName.equals(that.indexName)
                    && unique == that.unique
                    && columns.equals(that.columns);
        }

        return false;
    }
}
