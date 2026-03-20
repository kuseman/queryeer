package se.kuseman.payloadbuilder.catalog.jdbc;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.queryeer.api.extensions.visualization.graph.Graph;
import com.queryeer.api.extensions.visualization.graph.GraphEdge;
import com.queryeer.api.extensions.visualization.graph.GraphProperty;
import com.queryeer.api.extensions.visualization.graph.GraphVertex;

import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Column;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Constraint;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKey;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKeyColumn;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Index;
import se.kuseman.payloadbuilder.catalog.jdbc.model.IndexColumn;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;

/** Builds a visualization {@link Graph} from a JDBC {@link Catalog}. */
class DatabaseSchemaGraph
{
    private static final Set<String> SCHEMAS_TO_SKIP = Set.of("sys", "information_schema");

    private DatabaseSchemaGraph()
    {
    }

    static Graph buildGraph(String database, Catalog catalog)
    {
        List<GraphVertex> vertices = new ArrayList<>();

        // Index table sources by their id for FK edge lookup
        Map<String, GraphVertex> vertexById = new HashMap<>();

        // Build indices and constraints lookup by table name
        Map<String, List<Index>> indicesByTable = new HashMap<>();
        for (Index index : catalog.getIndices())
        {
            indicesByTable.computeIfAbsent(vertexId(index.getObjectName()), k -> new ArrayList<>())
                    .add(index);
        }

        Map<String, List<Constraint>> constraintsByTable = new HashMap<>();
        for (Constraint constraint : catalog.getConstraints())
        {
            constraintsByTable.computeIfAbsent(vertexId(constraint.getObjectName()), k -> new ArrayList<>())
                    .add(constraint);
        }

        for (TableSource ts : catalog.getTableSources())
        {
            if (SCHEMAS_TO_SKIP.contains(lowerCase(ts.getSchema())))
            {
                continue;
            }

            String id = vertexId(ts);
            List<GraphProperty> properties = buildTableProperties(ts, indicesByTable.getOrDefault(id, List.of()), constraintsByTable.getOrDefault(id, List.of()));
            GraphVertex vertex = new GraphVertex(id, vertexLabel(ts), "", null, List.of(), properties);
            vertices.add(vertex);
            vertexById.put(id, vertex);
        }

        List<GraphEdge> edges = new ArrayList<>();
        // Build edges from foreign keys
        for (ForeignKey fk : catalog.getForeignKeys())
        {
            if (fk.getColumns()
                    .isEmpty())
            {
                continue;
            }

            ForeignKeyColumn firstCol = fk.getColumns()
                    .get(0);
            String sourceId = vertexId(firstCol.getConstrainedObjectName());
            String targetId = vertexId(firstCol.getReferencedObjectName());

            // Skip FKs where one side is outside this catalog (cross-database FK)
            if (!vertexById.containsKey(sourceId)
                    || !vertexById.containsKey(targetId))
            {
                continue;
            }

            List<GraphProperty> fkProperties = buildFkProperties(fk);
            edges.add(new GraphEdge(sourceId + "."
                                    + fk.getObjectName()
                                            .getName(),
                    sourceId, targetId, "", "", null, fkProperties));
        }

        return new Graph(database + " — Schema Diagram", vertices, edges);
    }

    private static String vertexId(ObjectName name)
    {
        if (isBlank(name.getSchema()))
        {
            return name.getName();
        }
        return name.getSchema() + "." + name.getName();
    }

    private static String vertexLabel(TableSource ts)
    {
        return isBlank(ts.getSchema()) ? ts.getName()
                : ts.getSchema() + "." + ts.getName();
    }

    private static List<GraphProperty> buildTableProperties(TableSource ts, List<Index> indices, List<Constraint> constraints)
    {
        List<GraphProperty> properties = new ArrayList<>();

        // Type
        properties.add(new GraphProperty("", "Type", ts.getType()
                .name(), true));

        // Columns
        if (!ts.getColumns()
                .isEmpty())
        {
            GraphProperty colsProp = new GraphProperty("Columns", "Columns", null, false, false, buildColumnSubProperties(ts.getColumns()));
            properties.add(colsProp);
        }

        // Indices
        if (!indices.isEmpty())
        {
            List<GraphProperty> indexSubProps = new ArrayList<>();
            for (Index index : indices)
            {
                String colList = buildIndexColumnList(index.getColumns());
                String label = (index.isUnique() ? "UNIQUE "
                        : "") + colList;
                indexSubProps.add(new GraphProperty("Indices", index.getIndexName(), label, false));
            }
            properties.add(new GraphProperty("Indices", "Indices", null, false, false, indexSubProps));
        }

        // Constraints
        if (!constraints.isEmpty())
        {
            List<GraphProperty> constraintSubProps = new ArrayList<>();
            for (Constraint c : constraints)
            {
                String value = c.getType()
                        .name()
                        + (isBlank(c.getColumnName()) ? ""
                                : " on " + c.getColumnName())
                        + (isBlank(c.getDefinition()) ? ""
                                : ": " + c.getDefinition());
                constraintSubProps.add(new GraphProperty("Constraints", c.getName(), value, false));
            }
            properties.add(new GraphProperty("Constraints", "Constraints", null, false, false, constraintSubProps));
        }

        return properties;
    }

    private static List<GraphProperty> buildColumnSubProperties(List<Column> columns)
    {
        List<GraphProperty> subProps = new ArrayList<>();
        for (Column col : columns)
        {
            String prefix = isBlank(col.getPrimaryKeyName()) ? ""
                    : "PK ";
            subProps.add(new GraphProperty("Columns", prefix + col.getName(), col.getDefinition(), true));
        }
        return subProps;
    }

    private static String buildIndexColumnList(List<IndexColumn> cols)
    {
        if (cols.isEmpty())
        {
            return "";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < cols.size(); i++)
        {
            if (i > 0)
            {
                sb.append(", ");
            }
            IndexColumn c = cols.get(i);
            sb.append(c.getName());
            if (!c.isAscending())
            {
                sb.append(" DESC");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private static List<GraphProperty> buildFkProperties(ForeignKey fk)
    {
        List<GraphProperty> props = new ArrayList<>();
        for (ForeignKeyColumn col : fk.getColumns())
        {
            props.add(new GraphProperty("Columns", col.getConstrainedColumnName(), "→ " + col.getReferencedObjectName()
                    .getName() + "." + col.getReferencedColumnName(), true));
        }
        return props;
    }
}
