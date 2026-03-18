package com.queryeer.api.extensions.visualization.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * A directed edge between two vertices in a {@link Graph}.
 *
 * @param id Unique identifier within the graph
 * @param sourceId Id of the source {@link GraphVertex}
 * @param targetId Id of the target {@link GraphVertex}
 * @param label Optional label rendered on the edge
 * @param toolTip Custom tooltip. Leave blank to auto-generate from properties.
 * @param style Optional mxGraph style string. Null uses the default edge style.
 * @param properties Properties shown in the property sheet when the edge is selected
 */
public record GraphEdge(String id, String sourceId, String targetId, String label, String toolTip, String style, List<GraphProperty> properties)
{
    public GraphEdge(String id, String sourceId, String targetId)
    {
        this(id, sourceId, targetId, "", "", null, new ArrayList<>());
    }

    public GraphEdge(String id, String sourceId, String targetId, List<GraphProperty> properties)
    {
        this(id, sourceId, targetId, "", "", null, properties);
    }

    public GraphEdge(String id, String sourceId, String targetId, String label, List<GraphProperty> properties)
    {
        this(id, sourceId, targetId, label, "", null, properties);
    }
}
