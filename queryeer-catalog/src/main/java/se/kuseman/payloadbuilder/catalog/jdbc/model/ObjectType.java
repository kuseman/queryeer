package se.kuseman.payloadbuilder.catalog.jdbc.model;

/** SQL Object types */
public enum ObjectType
{
    TABLE,
    VIEW,
    SYNONYM,
    TRIGGER,
    FUNCTION,
    BUILT_IN_FUNCTION,
    PROCEDURE;

    /** Returns true if this is a table source */
    public boolean isTableSource()
    {
        return this == TABLE
                || this == VIEW
                || this == SYNONYM;
    }

    /** Returns true if this is a procedure or function */
    public boolean isUserDefinedFunctionProcedure()
    {
        return this == FUNCTION
                || this == PROCEDURE;
    }
}