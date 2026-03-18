package com.queryeer.api.extensions.visualization.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * A vertex in a {@link Graph}.
 *
 * @param id Unique identifier within the graph
 * @param label Text displayed on the vertex
 * @param toolTip Custom tooltip. Leave blank to auto-generate from properties.
 * @param style Optional mxGraph style string. Null uses the default vertex style.
 * @param overlays Overlay icons rendered on top of the vertex
 * @param properties Properties shown in the property sheet when the vertex is selected
 */
public record GraphVertex(String id, String label, String toolTip, String style, List<GraphOverlay> overlays, List<GraphProperty> properties)
{
    public GraphVertex(String id, String label, List<GraphProperty> properties)
    {
        this(id, label, "", null, new ArrayList<>(), properties);
    }

    public GraphVertex(String id, String label, String toolTip, List<GraphProperty> properties)
    {
        this(id, label, toolTip, null, new ArrayList<>(), properties);
    }
}
