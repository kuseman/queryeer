package se.kuseman.payloadbuilder.catalog.jdbc;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.queryeer.api.extensions.visualization.graph.Graph;
import com.queryeer.api.extensions.visualization.graph.GraphEdge;
import com.queryeer.api.extensions.visualization.graph.GraphProperty;
import com.queryeer.api.extensions.visualization.graph.GraphVertex;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Routine;

/** Builds a visualization {@link Graph} showing the call hierarchy between stored procedures and functions. */
class RoutineCallGraph
{
    //@formatter:off
    private static final String STYLE_PROCEDURE = "fillColor=#dae8fc;strokeColor=#6c8ebf;rounded=true;shadow=true;fontStyle=1;";
    private static final String STYLE_FUNCTION   = "fillColor=#d5e8d4;strokeColor=#82b366;rounded=true;shadow=true;fontStyle=2;";
    private static final String STYLE_GHOST      = "fillColor=#f5f5f5;strokeColor=#999999;fontColor=#999999;rounded=true;dashed=true;";
    //@formatter:on

    private RoutineCallGraph()
    {
    }

    /**
     * Build a call-graph starting from {@code selectedRoots}. Only the selected routines and their transitively reachable callees are parsed — not the entire catalog.
     */
    static Graph buildGraph(String database, List<Routine> selectedRoots, Catalog catalog, JdbcDialect dialect, IConnectionContext connectionContext)
    {
        // Normalised key → Routine lookup for catalog boundary checks
        Map<String, Routine> routineByKey = new LinkedHashMap<>();
        for (Routine r : catalog.getRoutines())
        {
            routineByKey.put(routineKey(r), r);
        }

        // Fetch every routine body in a single DB query
        Map<String, String> allBodies = dialect.getRoutineBodies(connectionContext, catalog.getRoutines());

        // BFS: parse only reachable routines starting from the selected roots
        Set<String> processed = new HashSet<>();
        Map<String, String> frontier = new LinkedHashMap<>();
        for (Routine r : selectedRoots)
        {
            String key = routineKey(r);
            String body = allBodies.get(key);
            if (body != null)
            {
                frontier.put(key, body);
            }
            else
            {
                processed.add(key);
            }
        }

        Map<String, List<ObjectName>> allCalls = new LinkedHashMap<>();
        while (!frontier.isEmpty())
        {
            Map<String, List<ObjectName>> batch = dialect.extractAllRoutineCalls(frontier, connectionContext);
            allCalls.putAll(batch);
            processed.addAll(frontier.keySet());

            Map<String, String> nextFrontier = new LinkedHashMap<>();
            for (List<ObjectName> calls : batch.values())
            {
                for (ObjectName called : calls)
                {
                    String key = routineKey(called);
                    if (routineByKey.containsKey(key)
                            && !processed.contains(key)
                            && !nextFrontier.containsKey(key))
                    {
                        String body = allBodies.get(key);
                        if (body != null)
                        {
                            nextFrontier.put(key, body);
                        }
                        else
                        {
                            processed.add(key);
                        }
                    }
                }
            }
            frontier = nextFrontier;
        }

        // Vertices for all processed catalog routines
        Map<String, GraphVertex> vertexById = new LinkedHashMap<>();
        for (String key : processed)
        {
            Routine r = routineByKey.get(key);
            if (r != null)
            {
                vertexById.put(key, new GraphVertex(key, vertexLabel(r), "", vertexStyle(r), List.of(), buildProperties(r)));
            }
        }
        // Ensure selected roots always appear even when they had no parseable body
        for (Routine r : selectedRoots)
        {
            String key = routineKey(r);
            if (!vertexById.containsKey(key))
            {
                vertexById.put(key, new GraphVertex(key, vertexLabel(r), "", vertexStyle(r), List.of(), buildProperties(r)));
            }
        }

        // Edges + ghost vertices for out-of-catalog callees
        Set<String> edgeIds = new HashSet<>();
        List<GraphEdge> edges = new ArrayList<>();
        for (Map.Entry<String, List<ObjectName>> entry : allCalls.entrySet())
        {
            String sourceId = entry.getKey();
            for (ObjectName called : entry.getValue())
            {
                String targetKey = routineKey(called);
                String edgeId = sourceId + "->" + targetKey;
                if (!edgeIds.add(edgeId))
                {
                    continue;
                }
                if (!vertexById.containsKey(targetKey))
                {
                    String label = isBlank(called.getSchema()) ? called.getName()
                            : called.getSchema() + "." + called.getName();
                    vertexById.put(targetKey, new GraphVertex(targetKey, label, "", STYLE_GHOST, List.of(), List.of()));
                }
                edges.add(new GraphEdge(edgeId, sourceId, targetKey));
            }
        }

        return new Graph(database + " — Routine Call Graph", new ArrayList<>(vertexById.values()), edges);
    }

    private static String routineKey(ObjectName name)
    {
        String schema = stripBrackets(name.getSchema());
        String n = stripBrackets(name.getName());
        return isBlank(schema) ? lowerCase(n)
                : lowerCase(schema) + "." + lowerCase(n);
    }

    private static String stripBrackets(String s)
    {
        if (s == null)
        {
            return null;
        }
        String trimmed = s.trim();
        if (trimmed.startsWith("[")
                && trimmed.endsWith("]"))
        {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String vertexLabel(Routine r)
    {
        return isBlank(r.getSchema()) ? r.getName()
                : r.getSchema() + "." + r.getName();
    }

    private static String vertexStyle(Routine r)
    {
        return r.getType() == Routine.Type.PROCEDURE ? STYLE_PROCEDURE
                : STYLE_FUNCTION;
    }

    private static List<GraphProperty> buildProperties(Routine r)
    {
        List<GraphProperty> properties = new ArrayList<>();
        properties.add(new GraphProperty("", "Type", r.getType()
                .name(), true));

        if (!r.getParameters()
                .isEmpty())
        {
            List<GraphProperty> paramProps = new ArrayList<>();
            for (var param : r.getParameters())
            {
                String flags = (param.isOutput() ? "OUT "
                        : "")
                        + (param.isNullable() ? "NULL"
                                : "NOT NULL");
                paramProps.add(new GraphProperty("Parameters", param.getName(), param.getType() + " " + flags, false));
            }
            properties.add(new GraphProperty("Parameters", "Parameters", null, false, false, paramProps));
        }

        return properties;
    }
}
