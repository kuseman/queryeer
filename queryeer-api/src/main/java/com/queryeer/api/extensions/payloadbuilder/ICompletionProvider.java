package com.queryeer.api.extensions.payloadbuilder;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;

import se.kuseman.payloadbuilder.api.QualifiedName;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;

/** Provider for auto completion for a {@link ICatalogExtension} */
public interface ICompletionProvider
{
    /**
     * Return cache key for table meta completions for provided session.
     * 
     * <pre>
     * This should return a value that can be used as a key in a {@link Map} that identifies
     * what is needed to fetch table meta.
     * For example URL/databasename/username/password etc.
     * </pre>
     *
     * @return Returns the cache key or null if this provided doesn't need caching
     */
    Object getTableMetaCacheKey(IQuerySession session, String catalogAlias);

    /**
     * Return description of the completion from provided session and catalog alias. This is used in task dialog to show info about the loading of completions
     */
    default String getDescription(IQuerySession session, String catalogAlias)
    {
        return catalogAlias;
    }

    /**
     * Returns true if this completion provider is enabled with provided session and alias. This is a place to skip auto completions when for example vital properties are missed in session
     * (urls/usernames etc.)
     */
    default boolean enabled(IQuerySession session, String catalogAlias)
    {
        return true;
    }

    /**
     * Return meta of table completions for provided session and catalog alias.
     */
    default List<TableMeta> getTableCompletionMeta(IQuerySession querySession, String catalogAlias)
    {
        return emptyList();
    }

    /** Meta data for a table used in auto completions in Queyeer */
    public class TableMeta
    {
        private final QualifiedName name;
        /** HTML description of this table. Used in auto complete popup to describe table */
        private final String description;
        /** HTML description of this table. Used in tooltip when hoovering table */
        private final String tooltip;
        private final List<ColumnMeta> columns;

        public TableMeta(QualifiedName name, String description, String tooltip, List<ColumnMeta> columns)
        {
            this.name = requireNonNull(name, "name");
            this.description = description;
            this.tooltip = tooltip;
            this.columns = requireNonNull(columns, "columns");
        }

        public QualifiedName getName()
        {
            return name;
        }

        public String getDescription()
        {
            return description;
        }

        public String getTooltip()
        {
            return tooltip;
        }

        public List<ColumnMeta> getColumns()
        {
            return columns;
        }
    }

    /** Meta data for a column used in auto completions in Queyeer */
    public class ColumnMeta
    {
        private final QualifiedName name;
        /** HTML description of this column. Used in auto complete popup to describe column */
        private final String description;
        /** HTML description of this table. Used in tooltip when hoovering table */
        private final String tooltip;

        public ColumnMeta(QualifiedName name, String description, String tooltip)
        {
            this.name = requireNonNull(name, "name");
            this.description = description;
            this.tooltip = tooltip;
        }

        public QualifiedName getName()
        {
            return name;
        }

        public String getDescription()
        {
            return description;
        }

        public String getTooltip()
        {
            return tooltip;
        }
    }
}
