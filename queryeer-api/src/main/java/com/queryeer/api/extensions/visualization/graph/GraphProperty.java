package com.queryeer.api.extensions.visualization.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * A property of a graph vertex or edge.
 *
 * @param category Category of property
 * @param name Name of property
 * @param description Description of property
 * @param value Value of property
 * @param includeInToolTip Should this property be included in the auto-generated tooltip
 * @param subProperties Sub properties of this property
 */
public record GraphProperty(String category, String name, String description, Object value, boolean includeInToolTip, List<GraphProperty> subProperties)
{
    public GraphProperty(String category, String name, String description, Object value)
    {
        this(category, name, description, value, false, new ArrayList<>());
    }

    public GraphProperty(String name, Object value)
    {
        this("", name, "", value, false, new ArrayList<>());
    }

    public GraphProperty(String name, Object value, boolean includeInToolTip)
    {
        this("", name, "", value, includeInToolTip, new ArrayList<>());
    }

    public GraphProperty(String category, String name, Object value, boolean includeInToolTip)
    {
        this(category, name, "", value, includeInToolTip, new ArrayList<>());
    }
}
