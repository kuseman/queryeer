package se.kuseman.payloadbuilder.catalog.jdbc.model;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.List;

/** Base class for all table sources */
public class TableSource extends ObjectName
{
    private final List<Column> columns;
    private final Type type;

    public TableSource(String catalog, String schema, String name, Type type, List<Column> columns)
    {
        super(catalog, schema, name);
        this.type = requireNonNull(type, "type");
        this.columns = unmodifiableList(requireNonNull(columns, "columns"));
    }

    public Type getType()
    {
        return type;
    }

    public List<Column> getColumns()
    {
        return columns;
    }

    @Override
    public int hashCode()
    {
        return super.hashCode();
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
        else if (obj instanceof TableSource that)
        {
            return super.equals(obj)
                    && type == that.type;
        }
        return false;
    }

    /** Type of table source */
    public enum Type
    {
        TABLE,
        VIEW,
        SYNONYM,
        TABLEFUNCTION,
        UNKNOWN
    }
}
