package com.queryeer.output.queryplan;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.equalsAnyIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.l2fprod.common.propertysheet.DefaultProperty;
import com.l2fprod.common.propertysheet.Property;
import com.l2fprod.common.propertysheet.PropertySheet;
import com.l2fprod.common.propertysheet.PropertySheetPanel;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxCellOverlay;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxImageBundle;
import com.mxgraph.view.mxGraph;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputComponent;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputExtension;
import com.queryeer.api.extensions.output.queryplan.Node;
import com.queryeer.api.extensions.output.queryplan.Node.NodeLink;
import com.queryeer.api.extensions.output.queryplan.Node.NodeProperty;

@SuppressWarnings("deprecation")
class QueryPlanOutputComponent implements IQueryPlanOutputComponent
{
    private final IQueryPlanOutputExtension extension;
    private final QueryPlansPanel component;

    QueryPlanOutputComponent(IQueryPlanOutputExtension extension)
    {
        this.extension = requireNonNull(extension, "extension");
        this.component = new QueryPlansPanel();
    }

    @Override
    public String title()
    {
        return "Query Plan";
    }

    @Override
    public Icon icon()
    {
        return FontIcon.of(FontAwesome.OBJECT_GROUP);
    }

    @Override
    public IOutputExtension getExtension()
    {
        return extension;
    }

    @Override
    public Component getComponent()
    {
        return component;
    }

    @Override
    public void addQueryPlan(Node plan)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            component.addQueryPlan(plan);
        }
        else
        {
            SwingUtilities.invokeLater(() -> component.addQueryPlan(plan));
        }
    }

    @Override
    public void clearState()
    {
        Runnable r = () ->
        {
            component.tabbedPane.removeAll();
            component.revalidate();
            component.repaint();
        };

        if (SwingUtilities.isEventDispatchThread())
        {
            r.run();
        }
        else
        {
            SwingUtilities.invokeLater(r);
        }
    }

    /** Main panel with plans. One tab per query plan */
    class QueryPlansPanel extends JPanel
    {
        private final JTabbedPane tabbedPane;

        QueryPlansPanel()
        {
            super(new BorderLayout());

            tabbedPane = new JTabbedPane();
            tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

            add(tabbedPane, BorderLayout.CENTER);
        }

        void addQueryPlan(Node queryPlan)
        {
            int tabNumber = tabbedPane.getTabCount();
            String title = "Query #" + (tabNumber + 1);

            String statementText = queryPlan.properties()
                    .stream()
                    .filter(p -> NodeProperty.STATEMENT_TEXT.equalsIgnoreCase(p.name()))
                    .findAny()
                    .map(NodeProperty::value)
                    .map(String::valueOf)
                    .orElse("");

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new QueryPlanPanel(queryPlan), BorderLayout.CENTER);
            if (!isBlank(statementText))
            {
                JTextField lblStmTest = new JTextField(statementText);
                lblStmTest.setBackground(Color.WHITE);
                lblStmTest.setEditable(false);
                lblStmTest.setToolTipText("<html><p width=\"500\">" + StringEscapeUtils.escapeHtml4(statementText) + "</p></html>");

                panel.add(lblStmTest, BorderLayout.NORTH);
            }
            tabbedPane.addTab(title, panel);
            // The tabbed panes minimum size increases for every query plan added and that makes
            // the parent tabbed pane un-resizable so force the minimum size
            tabbedPane.setMinimumSize(new Dimension(100, 100));
        }
    }

    class QueryPlanPanel extends JPanel
    {
        private static final String GENERATED_TOOL_TIP = "__generatedToolTip";
        // CSOFF
        private static final String VERTEX_STYLE = "spacing=1;shape=image;image=box;verticalLabelPosition=bottom;imageVerticalAlign=middle;imageAlign=center;verticalAlign=top;orthogonal=1;fontColor=#00000;";
        // CSON

        private static final Set<String> INTERNAL_PROPERTIES = Set.of(NodeProperty.HAS_WARNINGS.toLowerCase(), NodeProperty.PARALLEL.toLowerCase(), NodeProperty.ROW_COUNT.toLowerCase(),
                NodeProperty.STATEMENT_TEXT.toLowerCase(), GENERATED_TOOL_TIP.toLowerCase());

        private static final DecimalFormat TOOLTIP_DOUBLE_FORMAT = new DecimalFormat("##.########");
        private static final mxImageBundle IMAGEBUNDLE;
        private static final ImageIcon ARROWS_LEFT;
        private static final ImageIcon YELLOW_WARNING;
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
        private static String TOOLTIP_FOOTER = """
                </table>
                </html>
                """;

        static
        {
            ImageIcon arrowsLeft = null;
            ImageIcon yellowWarning = null;
            IMAGEBUNDLE = new mxImageBundle();
            try
            {
                IMAGEBUNDLE.putImage("box", "data:image/png," + Base64.getEncoder()
                        .encodeToString(IOUtils.toByteArray(QueryPlanOutputComponent.class.getResourceAsStream("/icons/box.png"))));

                arrowsLeft = new ImageIcon(ImageIO.read(QueryPlanOutputComponent.class.getResourceAsStream("/icons/arrowsleft.png")));
                yellowWarning = new ImageIcon(ImageIO.read(QueryPlanOutputComponent.class.getResourceAsStream("/icons/yellowwarning.png")));
            }
            catch (IOException e)
            {
            }

            ARROWS_LEFT = arrowsLeft;
            YELLOW_WARNING = yellowWarning;
        }

        private final JLabel sheetTitle;
        private final PropertySheetPanel sheet;

        QueryPlanPanel(Node plan)
        {
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
            splitPane.setResizeWeight(0.9d);
            splitPane.setRightComponent(sheetPanel);
            splitPane.setLeftComponent(addQueryPlan(plan));

            add(splitPane, BorderLayout.CENTER);
        }

        private mxGraphComponent addQueryPlan(Node plan)
        {
            mxGraphComponent graphComponent = createGraphComponent();
            mxGraph graph = graphComponent.getGraph();
            graph.addImageBundle(IMAGEBUNDLE);

            Object parent = graph.getDefaultParent();

            List<Node> queue = new ArrayList<>();
            queue.add(plan);

            graph.getModel()
                    .beginUpdate();
            try
            {

                Object cell = graph.insertVertex(parent, null, plan, 1, 1, 80, 80, VERTEX_STYLE);
                addOverlays(graphComponent, cell, plan);
                addNodes(graph, graphComponent, parent, cell, plan);
                mxHierarchicalLayout layout = new mxHierarchicalLayout(graph, SwingConstants.WEST);
                layout.setFineTuning(true);
                layout.setIntraCellSpacing(50.0);
                layout.setInterHierarchySpacing(50.0);
                layout.setInterRankCellSpacing(50.0);
                layout.execute(graph.getDefaultParent());

                graph.updateCellSize(cell);
            }
            finally
            {
                graph.getModel()
                        .endUpdate();
            }

            return graphComponent;
        }

        private void addOverlays(mxGraphComponent graphComponent, Object cell, Node node)
        {
            if (node.hasWarnings())
            {
                mxCellOverlay overlay = new mxCellOverlay(YELLOW_WARNING, null);
                overlay.setAlign(mxConstants.ALIGN_RIGHT);
                overlay.setVerticalAlign(mxConstants.ALIGN_TOP);
                graphComponent.addCellOverlay(cell, overlay);
            }
            if (node.parallellExecution())
            {
                mxCellOverlay overlay = new mxCellOverlay(ARROWS_LEFT, null);
                overlay.setAlign(mxConstants.ALIGN_RIGHT);
                overlay.setVerticalAlign(mxConstants.ALIGN_BOTTOM);
                graphComponent.addCellOverlay(cell, overlay);
            }
        }

        private void addNodes(mxGraph graph, mxGraphComponent graphComponent, Object graphParent, Object graphParentCell, Node parent)
        {
            for (NodeLink link : parent.children())
            {
                Object cell = graph.insertVertex(graphParent, null, link.node(), 1, 1, 80, 80, VERTEX_STYLE);
                addOverlays(graphComponent, cell, link.node());
                graph.updateCellSize(cell);

                // Connect current child and parent
                graph.insertEdge(graphParent, null, link, graphParentCell, cell, getEdgeStyle(link));

                addNodes(graph, graphComponent, graphParent, cell, link.node());
            }
        }

        String getEdgeStyle(NodeLink link)
        {
            // 1 pixel per 10 factor
            int rowCount = link.getRowCount();
            boolean zeroRowCount = rowCount <= 0;
            int thickNess = 0;
            while (rowCount > 0)
            {
                thickNess++;
                rowCount /= 10;
            }
            if (thickNess == 0)
            {
                thickNess++;
            }

            return String.format("dashed=%s;strokeColor=#000000;startArrow=open;endArrow=none;strokeWidth=%d;", zeroRowCount ? "true"
                    : "false", thickNess);
        }

        private static String formatValueForTooltip(Object value)
        {
            if (value instanceof Double d)
            {
                return TOOLTIP_DOUBLE_FORMAT.format(d);
            }
            return String.valueOf(value);
        }

        private String generateToolTip(String title, List<NodeProperty> properties)
        {
            NodeProperty generatedTooltip = properties.stream()
                    .filter(p -> GENERATED_TOOL_TIP.equalsIgnoreCase(p.name()))
                    .findFirst()
                    .orElse(null);
            if (generatedTooltip != null)
            {
                return (String) generatedTooltip.value();
            }

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

            properties.sort(Comparator.comparing(p -> defaultIfBlank(p.category(), "")));

            String prevCategory = null;
            boolean propsAdded = false;
            for (NodeProperty prop : properties)
            {
                if (!prop.includeInToolTip())
                {
                    continue;
                }

                // Render category if present or changed
                String currentCategory = defaultIfBlank(prop.category(), "");
                if (prevCategory == null
                        || !equalsAnyIgnoreCase(prevCategory, currentCategory))
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

                // Flatten all sub properties
                List<NodeProperty> tooltipProps = new ArrayList<>();
                if (prop.value() != null)
                {
                    tooltipProps.add(prop);
                }
                List<NodeProperty> subProps = new ArrayList<>();
                subProps.addAll(ObjectUtils.defaultIfNull(prop.subProperties(), List.of()));
                while (!subProps.isEmpty())
                {
                    NodeProperty subProp = subProps.remove(0);
                    if (subProp.value() != null)
                    {
                        tooltipProps.add(subProp);
                    }
                    subProps.addAll(ObjectUtils.defaultIfNull(subProp.subProperties(), List.of()));
                }

                for (NodeProperty toolTipProp : tooltipProps)
                {
                    propsAdded = true;
                    sb.append("""
                            <tr>
                                <td>
                                %s
                                </td>
                                <td>
                                %s
                                </td>
                            </tr>
                            """.formatted(toolTipProp.name(), formatValueForTooltip(toolTipProp.value())));
                }
            }

            sb.append(TOOLTIP_FOOTER);

            String toolTip = propsAdded ? sb.toString()
                    : "";
            properties.add(new NodeProperty(GENERATED_TOOL_TIP, toolTip));
            return toolTip;
        }

        private mxGraphComponent createGraphComponent()
        {
            mxGraph graph = new mxGraph()
            {
                @Override
                public String getToolTipForCell(Object cell)
                {
                    if (cell instanceof mxCell mxcell
                            && mxcell.getValue() instanceof Node node)
                    {
                        if (isBlank(node.toolTip()))
                        {
                            return generateToolTip(node.label(), node.properties());
                        }

                        return node.toolTip();
                    }
                    else if (cell instanceof mxCell mxcell
                            && mxcell.getValue() instanceof NodeLink link)
                    {
                        if (isBlank(link.toolTip()))
                        {
                            return generateToolTip("", link.properties());
                        }
                        return link.toolTip();
                    }
                    return super.getToolTipForCell(cell);
                }

                @Override
                public String convertValueToString(Object cell)
                {
                    if (cell instanceof mxCell mxcell
                            && mxcell.getValue() instanceof Node node)
                    {
                        return node.label();
                    }
                    else if (cell instanceof mxCell mxcell
                            && mxcell.getValue() instanceof NodeLink)
                    {
                        // Links has no text
                        return "";
                    }
                    return super.convertValueToString(cell);
                }
            };
            graph.setHtmlLabels(true);
            graph.setCellsEditable(false);
            graph.setVertexLabelsMovable(false);
            graph.setEdgeLabelsMovable(false);
            graph.setConnectableEdges(false);

            mxGraphComponent graphComponent = new mxGraphComponent(graph)
            {
                // Panning should be performed without any key downs
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
                        // Show no special cursors, query plans should be read only
                        @Override
                        public void setCursor(Cursor cursor)
                        {
                            return;
                        }
                    };
                }
            };

            graphComponent.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            graphComponent.setToolTips(true);

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

                            List<NodeProperty> properties = null;
                            String title = "";

                            if (cell instanceof mxCell mxcell
                                    && mxcell.getValue() instanceof Node node)
                            {
                                properties = node.properties();
                                title = node.label();
                            }
                            else if (cell instanceof mxCell mxcell
                                    && mxcell.getValue() instanceof NodeLink link)
                            {
                                properties = link.properties();
                                title = "Link";
                            }

                            if (properties != null)
                            {
                                sheetTitle.setText(title);

                                Property[] existingProperties = sheet.getProperties();
                                for (Property prop : existingProperties)
                                {
                                    sheet.removeProperty(prop);
                                }
                                for (NodeProperty prop : properties)
                                {
                                    // Don't show meta markers in the properties sheet
                                    if (INTERNAL_PROPERTIES.contains(StringUtils.lowerCase(prop.name())))
                                    {
                                        continue;
                                    }
                                    else if (prop.subProperties()
                                            .isEmpty()
                                            && prop.value() == null)
                                    {
                                        continue;
                                    }
                                    sheet.addProperty(toSheetProp(prop));
                                }
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

            graphComponent.setDragEnabled(false);
            graphComponent.setConnectable(false);
            graphComponent.setPanning(true);

            return graphComponent;
        }

        private DefaultProperty toSheetProp(NodeProperty prop)
        {
            DefaultProperty defaultProp = new DefaultProperty();
            defaultProp.setCategory(defaultIfBlank(prop.category(), ""));
            defaultProp.setName(prop.name());
            defaultProp.setDisplayName(prop.name());
            defaultProp.setShortDescription(defaultIfBlank(prop.description(), prop.value() == null ? ""
                    : String.valueOf(prop.value())));
            defaultProp.setValue(prop.value());

            for (NodeProperty sub : prop.subProperties())
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
    }
}