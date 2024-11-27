package com.queryeer.api.extensions.output.queryplan;

import java.util.ArrayList;
import java.util.List;

/**
 * Query plan node.
 *
 * @param label The label for the node in the graph.
 * @param toolTip Custom tooltip used for node. Leave blank to autogenerate from {@link NodeProperty}.
 * @param properties Properties of this node.
 * @param children Child nodes.
 */
public record Node(String label, String toolTip, List<Node.NodeProperty> properties, List<Node.NodeLink> children)
{
    public Node(String label, String toolTip, List<Node.NodeProperty> properties)
    {
        this(label, toolTip, properties, new ArrayList<>());
    }

    /** Return row count of this node. */
    public int getRowCount()
    {
        return properties.stream()
                .filter(p -> NodeProperty.ROW_COUNT.equalsIgnoreCase(p.name))
                .map(p -> ((Number) p.value).intValue())
                .findFirst()
                .orElse(0);
    }

    /** Return true if this node has warnings. */
    public boolean hasWarnings()
    {
        return properties.stream()
                .filter(p -> NodeProperty.HAS_WARNINGS.equalsIgnoreCase(p.name))
                .findFirst()
                .orElse(null) != null;

    }

    /** Return true if this node has been executed in a parallel execution mode. */
    public boolean parallellExecution()
    {
        return properties.stream()
                .filter(p -> NodeProperty.PARALLEL.equalsIgnoreCase(p.name))
                .findFirst()
                .orElse(null) != null;

    }

    /**
     * A property of a node/link.
     *
     * @param category Category of property
     * @param name Name of property
     * @param description Description of property
     * @param value Value of property
     * @param includeInToolTip Should this property be included in the generated tooltip of the owning node/link.
     * @param subProperties Sub properties of this property
     */
    public record NodeProperty(String category, String name, String description, Object value, boolean includeInToolTip, List<NodeProperty> subProperties)
    {

        /** Property that is used to hint about the row count of a node to draw thickness of edge etc. */
        public static final String ROW_COUNT = "__rowCount";
        /** Property that is used to hint about that node with this property has a warning. Adds overlay icon in graph. */
        public static final String HAS_WARNINGS = "__hasWarning";
        /** Property that is used to hint about that node with this property has been executed in parallel. Adds overlay icon in graph. */
        public static final String PARALLEL = "__parallel";
        /** Property that can be set on root plan node to show the plans query text places in UI. ie. the tab's tooltip to easy see which query the plan belongs to. */
        public static final String STATEMENT_TEXT = "__statementText";

        public NodeProperty(String category, String name, String description, Object value)
        {
            this(category, name, description, value, false, new ArrayList<>());
        }

        public NodeProperty(String name, Object value)
        {
            this("", name, "", value, false, new ArrayList<>());
        }

        public NodeProperty(String name, Object value, boolean includeInToolTip)
        {
            this("", name, "", value, includeInToolTip, new ArrayList<>());
        }

        public NodeProperty(String category, String name, Object value, boolean includeInToolTip)
        {
            this(category, name, "", value, includeInToolTip, new ArrayList<>());
        }
    }

    /** A link to a child node. */
    public record NodeLink(String toolTip, List<NodeProperty> properties, Node node)
    {
        /**
         * Row count for this link (ie. the rows that the node returned). This is used to draw thickness of rows
         */
        public int getRowCount()
        {
            return node.getRowCount();
        }
    }
}
