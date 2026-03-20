package com.queryeer.visualization.graph;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import com.l2fprod.common.propertysheet.DefaultProperty;
import com.l2fprod.common.propertysheet.Property;
import com.l2fprod.common.propertysheet.PropertySheet;
import com.l2fprod.common.propertysheet.PropertySheetPanel;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxCellOverlay;
import com.mxgraph.view.mxGraph;
import com.queryeer.Constants;
import com.queryeer.UiUtils;
import com.queryeer.api.extensions.visualization.graph.Graph;
import com.queryeer.api.extensions.visualization.graph.GraphEdge;
import com.queryeer.api.extensions.visualization.graph.GraphOverlay;
import com.queryeer.api.extensions.visualization.graph.GraphProperty;
import com.queryeer.api.extensions.visualization.graph.GraphVertex;

/** Reusable panel that renders a {@link Graph} using jGraphX with an interactive property sheet. */
public class GraphPanel extends JPanel
{
    // CSOFF
    private static final String DEFAULT_VERTEX_STYLE = "spacing=5;shape=label;verticalLabelPosition=center;imageVerticalAlign=middle;imageAlign=center;verticalAlign=top;orthogonal=1;fontColor=#00000;rounded=true;shadow=true";
    // CSON

    private static final String TOOLTIP_HEADER = """
            <html>
                <head>
                <style>
                    table, th, td {
                        border: 0px solid rgb(240, 240, 240);
                        border-collapse: collapse;
                        vertical-align: top;
                    }

                    table {
                        width: 200px;
                        max-width: 50px;
                        min-width: 100px;
                    }

                    tr {
                        border-bottom: 1px solid rgb(190, 190, 190);
                        margin-top: 0;
                        margin-bottom: 0;
                    }

                    th {
                        font-weight: bold;
                        border-bottom: 1px solid rgb(190, 190, 190);
                    }
                </style>
                </head>
                <table>
            """;
    private static final String TOOLTIP_FOOTER = """
            </table>
            </html>
            """;

    private final JLabel sheetTitle;
    private final PropertySheetPanel sheet;

    public GraphPanel(Graph graph)
    {
        requireNonNull(graph, "graph");
        setLayout(new BorderLayout());

        sheetTitle = new JLabel();

        JPanel sheetPanel = new JPanel(new BorderLayout());
        sheetPanel.add(sheetTitle, BorderLayout.NORTH);

        sheet = new PropertySheetPanel();
        sheet.setMode(PropertySheet.VIEW_AS_CATEGORIES);
        sheet.setDescriptionVisible(true);
        sheet.setSortingCategories(true);
        sheet.setSortingProperties(true);
        sheet.setRestoreToggleStates(true);

        sheetPanel.add(sheet, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.85d);
        splitPane.setRightComponent(sheetPanel);
        splitPane.setLeftComponent(buildGraphComponent(graph));

        add(splitPane, BorderLayout.CENTER);
    }

    private mxGraphComponent buildGraphComponent(Graph graph)
    {
        mxGraphComponent graphComponent = createGraphComponent();
        mxGraph mxgraph = graphComponent.getGraph();
        Object parent = mxgraph.getDefaultParent();

        mxgraph.getModel()
                .beginUpdate();
        try
        {
            // Insert all vertices, keyed by GraphVertex id
            Map<String, Object> cellById = new HashMap<>();
            for (GraphVertex vertex : graph.vertices())
            {
                String style = defaultIfBlank(vertex.style(), DEFAULT_VERTEX_STYLE);
                Object cell = mxgraph.insertVertex(parent, null, vertex, 1, 1, 80, 80, style);
                mxgraph.updateCellSize(cell);
                addOverlays(graphComponent, cell, vertex.overlays());
                cellById.put(vertex.id(), cell);
            }

            // Insert all edges
            for (GraphEdge edge : graph.edges())
            {
                Object source = cellById.get(edge.sourceId());
                Object target = cellById.get(edge.targetId());
                if (source == null
                        || target == null)
                {
                    continue;
                }
                String style = edge.style() != null ? edge.style()
                        : defaultEdgeStyle();
                mxgraph.insertEdge(parent, null, edge, source, target, style);
            }

            mxHierarchicalLayout layout = new mxHierarchicalLayout(mxgraph, graph.layoutDirection());
            layout.execute(parent);
        }
        finally
        {
            mxgraph.getModel()
                    .endUpdate();
        }

        return graphComponent;
    }

    private void addOverlays(mxGraphComponent graphComponent, Object cell, List<GraphOverlay> overlays)
    {
        for (GraphOverlay overlay : overlays)
        {
            mxCellOverlay mxOverlay = new mxCellOverlay(overlay.icon(), null);
            mxOverlay.setAlign(overlay.horizontalAlign());
            mxOverlay.setVerticalAlign(overlay.verticalAlign());
            graphComponent.addCellOverlay(cell, mxOverlay);
        }
    }

    private String defaultEdgeStyle()
    {
        String stroke = "#000000";
        if (UiUtils.isDarkLookAndFeel())
        {
            stroke = Constants.DARK_THEME_LIGHT_COLOR_HEX;
        }
        return "rounded=true;edgeStyle=entityRelationEdgeStyle;startArrow=none;endArrow=open;strokeColor=" + stroke + ";";
    }

    private String generateToolTip(String title, List<GraphProperty> props)
    {
        StringBuilder sb = new StringBuilder(TOOLTIP_HEADER);
        if (!isBlank(title))
        {
            sb.append("""
                    <tr>
                        <td colspan='2'>
                        %s
                        </td>
                    </tr>
                    """.formatted(title));
        }

        List<GraphProperty> properties = new ArrayList<>(props);
        properties.sort(Comparator.comparing(p -> defaultIfBlank(p.category(), "")));

        String prevCategory = null;
        boolean propsAdded = false;
        for (GraphProperty prop : properties)
        {
            if (!prop.includeInToolTip())
            {
                continue;
            }

            String currentCategory = defaultIfBlank(prop.category(), "");
            if (prevCategory == null
                    || !prevCategory.equalsIgnoreCase(currentCategory))
            {
                if (!isBlank(currentCategory))
                {
                    sb.append("""
                            <tr>
                                <td colspan='2'>
                                <b>%s</b>
                                </td>
                            </tr>
                            """.formatted(currentCategory));
                }
            }
            prevCategory = currentCategory;

            // Flatten sub properties into tooltip rows
            List<GraphProperty> tooltipProps = new ArrayList<>();
            if (prop.value() != null)
            {
                tooltipProps.add(prop);
            }
            List<GraphProperty> subProps = new ArrayList<>(prop.subProperties());
            while (!subProps.isEmpty())
            {
                GraphProperty subProp = subProps.remove(0);
                if (subProp.value() != null)
                {
                    tooltipProps.add(subProp);
                }
                subProps.addAll(0, subProp.subProperties());
            }

            for (GraphProperty tooltipProp : tooltipProps)
            {
                propsAdded = true;
                sb.append("""
                        <tr>
                            <td>%s</td>
                            <td>%s</td>
                        </tr>
                        """.formatted(tooltipProp.name(), tooltipProp.value()));
            }
        }

        sb.append(TOOLTIP_FOOTER);
        return propsAdded ? sb.toString()
                : "";
    }

    private void showProperties(String title, List<GraphProperty> properties)
    {
        sheetTitle.setText(title);
        Property[] existing = sheet.getProperties();
        for (Property prop : existing)
        {
            sheet.removeProperty(prop);
        }
        for (GraphProperty prop : properties)
        {
            if (prop.subProperties()
                    .isEmpty()
                    && prop.value() == null)
            {
                continue;
            }
            sheet.addProperty(toSheetProp(prop));
        }
    }

    private DefaultProperty toSheetProp(GraphProperty prop)
    {
        DefaultProperty defaultProp = new DefaultProperty();
        defaultProp.setCategory(defaultIfBlank(prop.category(), ""));
        defaultProp.setName(prop.name());
        defaultProp.setDisplayName(prop.name());
        defaultProp.setShortDescription(defaultIfBlank(prop.description(), prop.value() == null ? ""
                : String.valueOf(prop.value())));
        defaultProp.setValue(prop.value());

        for (GraphProperty sub : prop.subProperties())
        {
            if (sub.subProperties()
                    .isEmpty()
                    && sub.value() == null)
            {
                continue;
            }
            defaultProp.addSubProperty(toSheetProp(sub));
        }

        return defaultProp;
    }

    private mxGraphComponent createGraphComponent()
    {
        mxGraph graph = new mxGraph()
        {
            @Override
            public String getToolTipForCell(Object cell)
            {
                if (cell instanceof mxCell mxcell
                        && mxcell.getValue() instanceof GraphVertex vertex)
                {
                    if (isBlank(vertex.toolTip()))
                    {
                        return generateToolTip(vertex.label(), vertex.properties());
                    }
                    return vertex.toolTip();
                }
                else if (cell instanceof mxCell mxcell
                        && mxcell.getValue() instanceof GraphEdge edge)
                {
                    if (isBlank(edge.toolTip()))
                    {
                        return generateToolTip(edge.label(), edge.properties());
                    }
                    return edge.toolTip();
                }
                return super.getToolTipForCell(cell);
            }

            @Override
            public String convertValueToString(Object cell)
            {
                if (cell instanceof mxCell mxcell
                        && mxcell.getValue() instanceof GraphVertex vertex)
                {
                    return vertex.label();
                }
                else if (cell instanceof mxCell mxcell
                        && mxcell.getValue() instanceof GraphEdge edge)
                {
                    return defaultIfBlank(edge.label(), "");
                }
                return super.convertValueToString(cell);
            }
        };

        graph.setHtmlLabels(true);
        graph.setCellsEditable(false);
        graph.setCellsMovable(true);
        graph.setVertexLabelsMovable(false);
        graph.setEdgeLabelsMovable(false);
        graph.setConnectableEdges(false);

        mxGraphComponent graphComponent = new mxGraphComponent(graph)
        {
            @Override
            public boolean isPanningEvent(java.awt.event.MouseEvent event)
            {
                return event != null;
            }

            @Override
            protected mxGraphControl createGraphControl()
            {
                return new mxGraphControl()
                {
                };
            }
        };

        graphComponent.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        graphComponent.setToolTips(true);
        graphComponent.setConnectable(false);
        graphComponent.setPanning(true);
        graphComponent.setDragEnabled(false);

        graphComponent.getGraphControl()
                .addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(MouseEvent e)
                    {
                        Object cell = graphComponent.getCellAt(e.getX(), e.getY());
                        if (cell == null
                                && e.getClickCount() == 2)
                        {
                            graphComponent.zoomActual();
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent e)
                    {
                        Object cell = graphComponent.getCellAt(e.getX(), e.getY());
                        if (cell instanceof mxCell mxcell
                                && mxcell.getValue() instanceof GraphVertex vertex)
                        {
                            showProperties(vertex.label(), vertex.properties());
                        }
                        else if (cell instanceof mxCell mxcell
                                && mxcell.getValue() instanceof GraphEdge edge)
                        {
                            showProperties(defaultIfBlank(edge.label(), "Edge"), edge.properties());
                        }
                    }
                });

        graphComponent.addMouseWheelListener(new MouseWheelListener()
        {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                if (e.isControlDown()
                        || e.isMetaDown())
                {
                    if (e.getWheelRotation() < 0)
                    {
                        graphComponent.zoomIn();
                    }
                    else
                    {
                        graphComponent.zoomOut();
                    }
                }
            }
        });

        return graphComponent;
    }
}
