package se.kuseman.payloadbuilder.catalog.jdbc.model;

import static java.util.Objects.requireNonNull;

/** Model of an index column */
public class IndexColumn
{
    private final String name;
    private final boolean ascending;

    public IndexColumn(String name, boolean ascending)
    {
        this.name = requireNonNull(name, "name");
        this.ascending = ascending;
    }

    public String getName()
    {
        return name;
    }

    public boolean isAscending()
    {
        return ascending;
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
        else if (obj instanceof IndexColumn that)
        {
            return name.equals(that.name)
                    && ascending == that.ascending;
        }

        return false;
    }
}
