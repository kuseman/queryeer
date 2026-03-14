package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.stream.Collectors.joining;

import java.util.List;

import com.queryeer.api.extensions.assistant.IAIContextItem;

import se.kuseman.payloadbuilder.catalog.jdbc.model.Column;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Index;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;

class JdbcAITableSourceContextItem implements IAIContextItem
{
    private final TableSource tableSource;
    private final List<Index> indices;
    private final boolean defaultSelected;

    JdbcAITableSourceContextItem(TableSource tableSource, List<Index> indices, boolean defaultSelected)
    {
        this.tableSource = tableSource;
        this.indices = indices;
        this.defaultSelected = defaultSelected;
    }

    @Override
    public boolean isDefaultSelected()
    {
        return defaultSelected;
    }

    @Override
    public String getLabel()
    {
        return "%s.%s".formatted(tableSource.getSchema(), tableSource.getName());
    }

    @Override
    public String getGroup()
    {
        return switch (tableSource.getType())
        {
            case TABLE -> "Tables";
            case VIEW -> "Views";
            case SYNONYM -> "Synonyms";
            case TABLEFUNCTION -> "Table Functions";
            default -> "Other";
        };
    }

    @Override
    public String getContent()
    {
        /*
         * TABLE dbo.Table (col1 INT PrimaryKey, ....) Indices: ix_table_col1 (unique, columns:
         */
        StringBuilder sb = new StringBuilder();
        sb.append(tableSource.getType()
                .name()
                .toLowerCase())
                .append(" ")
                .append(getLabel())
                .append(" (");
        List<Column> columns = tableSource.getColumns();
        for (int i = 0; i < columns.size(); i++)
        {
            Column col = columns.get(i);
            if (i > 0)
            {
                sb.append(", ");
            }
            sb.append(col.getName())
                    .append(" ")
                    .append(col.getDefinition());
            if (col.getPrimaryKeyName() != null)
            {
                sb.append(" PrimaryKey");
            }
        }
        sb.append(")");
        if (!indices.isEmpty())
        {
            sb.append(" Indices: ");
            for (int i = 0; i < indices.size(); i++)
            {
                Index index = indices.get(i);
                if (i > 0)
                {
                    sb.append(", ");
                }
                sb.append(index.getIndexName())
                        .append(" (");
                if (index.isUnique())
                {
                    sb.append("unique, ");
                }
                sb.append("columns: ");
                sb.append(index.getColumns()
                        .stream()
                        .map(ic -> ic.getName() + (ic.isAscending() ? " ASC"
                                : " DESC"))
                        .collect(joining(",")));
                sb.append(")");
            }
        }
        return sb.toString();
    }
}
