package se.kuseman.payloadbuilder.catalog.jdbc.model;

import java.util.Set;

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

    public static Set<ObjectType> TABLE_SOURCE_TYPES = Set.of(TABLE, VIEW, SYNONYM);
    public static Set<ObjectType> FUNCTION_OR_PROCEDURE_TYPES = Set.of(FUNCTION, PROCEDURE);
}