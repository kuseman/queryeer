package se.kuseman.payloadbuilder.catalog.jdbc.model;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.List;

/** A catalog (database/schema) with a set of table sources and routines */
public class Catalog
{
    private final String name;
    private final List<TableSource> tableSources;
    private final List<Routine> routines;
    private final List<Index> indices;
    private final List<ForeignKey> foreignKeys;
    private final List<Constraint> constraints;

    public Catalog(String name, List<TableSource> tableSources, List<Routine> routines, List<Index> indices, List<ForeignKey> foreignKeys, List<Constraint> constraints)
    {
        this.name = requireNonNull(name, "name");
        this.tableSources = unmodifiableList(requireNonNull(tableSources, "tableSources"));
        this.routines = unmodifiableList(requireNonNull(routines, "routines"));
        this.indices = unmodifiableList(requireNonNull(indices, "indices"));
        this.foreignKeys = unmodifiableList(requireNonNull(foreignKeys, "foreignKeys"));
        this.constraints = unmodifiableList(requireNonNull(constraints, "constraints"));
    }

    public String getName()
    {
        return name;
    }

    public List<TableSource> getTableSources()
    {
        return tableSources;
    }

    public List<Routine> getRoutines()
    {
        return routines;
    }

    public List<Index> getIndices()
    {
        return indices;
    }

    public List<ForeignKey> getForeignKeys()
    {
        return foreignKeys;
    }

    public List<Constraint> getConstraints()
    {
        return constraints;
    }
}
