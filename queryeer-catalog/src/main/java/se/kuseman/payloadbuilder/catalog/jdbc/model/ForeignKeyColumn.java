package se.kuseman.payloadbuilder.catalog.jdbc.model;

import static java.util.Objects.requireNonNull;

/** Model of a foreign key column */
public class ForeignKeyColumn
{
    private final ObjectName constrainedObjectName;
    private final String constrainedColumnName;
    private final ObjectName referencedObjectName;
    private final String referencedColumnName;

    public ForeignKeyColumn(ObjectName constrainedObjectName, String constrainedColumnName, ObjectName referencedObjectName, String referencedColumnName)
    {
        this.constrainedObjectName = requireNonNull(constrainedObjectName, "constrainedObjectName");
        this.constrainedColumnName = requireNonNull(constrainedColumnName, "constrainedColumnName");
        this.referencedObjectName = requireNonNull(referencedObjectName, "referencedObjectName");
        this.referencedColumnName = requireNonNull(referencedColumnName, "referencedColumnName");
    }

    public ObjectName getConstrainedObjectName()
    {
        return constrainedObjectName;
    }

    public String getConstrainedColumnName()
    {
        return constrainedColumnName;
    }

    public ObjectName getReferencedObjectName()
    {
        return referencedObjectName;
    }

    public String getReferencedColumnName()
    {
        return referencedColumnName;
    }

    @Override
    public int hashCode()
    {
        return constrainedObjectName.hashCode();
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
        else if (obj instanceof ForeignKeyColumn that)
        {
            return constrainedObjectName.equals(that.constrainedObjectName)
                    && constrainedColumnName.equals(that.constrainedColumnName)
                    && referencedObjectName.equals(that.referencedObjectName)
                    && referencedColumnName.equals(that.referencedColumnName);
        }
        return false;
    }
}
