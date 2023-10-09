package se.kuseman.payloadbuilder.catalog.jdbc.model;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.List;

/** Base class for routines (functions/procedures) */
public class Routine extends ObjectName
{
    private final Type type;
    private final List<RoutineParameter> parameters;

    public Routine(String catalog, String schema, String name, Type type, List<RoutineParameter> parameters)
    {
        super(catalog, schema, name);
        this.type = requireNonNull(type, "type");
        this.parameters = unmodifiableList(requireNonNull(parameters, "parameters"));
    }

    public Type getType()
    {
        return type;
    }

    public List<RoutineParameter> getParameters()
    {
        return parameters;
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
        else if (obj instanceof Routine that)
        {
            return super.equals(obj)
                    && type == that.type;
        }
        return false;
    }

    /** Type of routine */
    public enum Type
    {
        PROCEDURE,
        FUNCTION
    }
}
