package com.queryeer.api.extensions.visualization.graph;

import java.util.List;

import javax.swing.SwingConstants;

/**
 * A graph with vertices and edges for use with {@link com.queryeer.api.service.IGraphVisualizationService}.
 *
 * @param title Title shown in the visualization dialog
 * @param layoutDirection Layout direction for the automatic layout. Use {@link SwingConstants#NORTH}, {@link SwingConstants#SOUTH}, {@link SwingConstants#EAST}, or {@link SwingConstants#WEST}.
 * Defaults to {@link SwingConstants#NORTH} (top-down).
 * @param vertices Vertices of the graph
 * @param edges Edges of the graph
 */
public record Graph(String title, int layoutDirection, List<GraphVertex> vertices, List<GraphEdge> edges)
{
    public Graph(String title, List<GraphVertex> vertices, List<GraphEdge> edges)
    {
        this(title, SwingConstants.NORTH, vertices, edges);
    }
}
