package com.queryeer.api.extensions.visualization.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.Action;

/**
 * A vertex in a {@link Graph}.
 *
 * @param id Unique identifier within the graph
 * @param label Text displayed on the vertex
 * @param toolTip Custom tooltip. Leave blank to auto-generate from properties.
 * @param style Optional mxGraph style string. Null uses the default vertex style.
 * @param overlays Overlay icons rendered on top of the vertex
 * @param properties Properties shown in the property sheet when the vertex is selected
 * @param actions Supplier of vertex actions. Use {@link #SHOW_IN_CONTEXT_MENU} on each action to control context-menu visibility.
 */
public record GraphVertex(String id, String label, String toolTip, String style, List<GraphOverlay> overlays, List<GraphProperty> properties, Supplier<List<Action>> actions)
{

    /**
     * {@link Action} key (value: {@link Boolean}) controlling whether the action appears in the vertex right-click context menu. Defaults to {@code true} when absent.
     */
    public static final String SHOW_IN_CONTEXT_MENU = "showInContextMenu";

    /**
     * {@link Action} key (value: {@link Boolean}) marking the action as the double-click handler for this vertex. The first action with this key set to {@code true} is invoked on double-click.
     */
    public static final String ON_DOUBLE_CLICK = "onDoubleClick";
    public GraphVertex(String id, String label, List<GraphProperty> properties)
    {
        this(id, label, "", null, new ArrayList<>(), properties, List::of);
    }

    public GraphVertex(String id, String label, String toolTip, List<GraphProperty> properties)
    {
        this(id, label, toolTip, null, new ArrayList<>(), properties, List::of);
    }

    public GraphVertex(String id, String label, String toolTip, String style, List<GraphOverlay> overlays, List<GraphProperty> properties)
    {
        this(id, label, toolTip, style, overlays, properties, List::of);
    }

    /** Returns a copy of this vertex with the given actions supplier. */
    public GraphVertex withActions(Supplier<List<Action>> actions)
    {
        return new GraphVertex(id, label, toolTip, style, overlays, properties, actions);
    }
}
