package com.queryeer.output.queryplan;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.commons.lang3.StringEscapeUtils;
import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.mxgraph.util.mxConstants;
import com.queryeer.Constants;
import com.queryeer.IconFactory;
import com.queryeer.UiUtils;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputComponent;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputExtension;
import com.queryeer.api.extensions.output.queryplan.Node;
import com.queryeer.api.extensions.output.queryplan.Node.NodeLink;
import com.queryeer.api.extensions.output.queryplan.Node.NodeProperty;
import com.queryeer.api.extensions.visualization.graph.Graph;
import com.queryeer.api.extensions.visualization.graph.GraphEdge;
import com.queryeer.api.extensions.visualization.graph.GraphOverlay;
import com.queryeer.api.extensions.visualization.graph.GraphProperty;
import com.queryeer.api.extensions.visualization.graph.GraphVertex;
import com.queryeer.visualization.graph.GraphPanel;

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
        return IconFactory.of(FontAwesome.OBJECT_GROUP);
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
                lblStmTest.setBackground(UIManager.getColor("TextField.background"));
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

    /** Converts a {@link Node} tree into a {@link Graph} and delegates rendering to {@link GraphPanel}. */
    class QueryPlanPanel extends JPanel
    {
        // Internal meta-property names used as rendering hints; excluded from the property sheet
        private static final Set<String> INTERNAL_PROPERTIES = Set.of(NodeProperty.HAS_WARNINGS.toLowerCase(), NodeProperty.PARALLEL.toLowerCase(), NodeProperty.ROW_COUNT.toLowerCase(),
                NodeProperty.STATEMENT_TEXT.toLowerCase());

        private static final ImageIcon ARROWS_LEFT;
        private static final ImageIcon YELLOW_WARNING;

        static
        {
            ARROWS_LEFT = new ImageIcon(toImage("/icons/arrowsleft.png"));
            YELLOW_WARNING = new ImageIcon(toImage("/icons/yellowwarning.png"));
        }

        QueryPlanPanel(Node plan)
        {
            setLayout(new BorderLayout());
            add(new GraphPanel(toGraph(plan)), BorderLayout.CENTER);
        }

        /** Convert the {@link Node} tree into a flat {@link Graph} for {@link GraphPanel}. */
        private Graph toGraph(Node root)
        {
            List<GraphVertex> vertices = new ArrayList<>();
            List<GraphEdge> edges = new ArrayList<>();
            collectNodes(root, null, vertices, edges, new AtomicInteger());
            return new Graph("Query Plan", SwingConstants.WEST, vertices, edges);
        }

        private String collectNodes(Node node, String parentId, List<GraphVertex> vertices, List<GraphEdge> edges, AtomicInteger idGen)
        {
            String id = String.valueOf(idGen.getAndIncrement());

            List<GraphOverlay> overlays = new ArrayList<>();
            if (node.hasWarnings())
            {
                overlays.add(new GraphOverlay(YELLOW_WARNING, mxConstants.ALIGN_RIGHT, mxConstants.ALIGN_TOP));
            }
            if (node.parallellExecution())
            {
                overlays.add(new GraphOverlay(ARROWS_LEFT, mxConstants.ALIGN_RIGHT, mxConstants.ALIGN_BOTTOM));
            }

            List<GraphProperty> props = toGraphProperties(node.properties(), INTERNAL_PROPERTIES);
            vertices.add(new GraphVertex(id, node.label(), node.toolTip(), null, overlays, props));

            for (NodeLink link : node.children())
            {
                String childId = collectNodes(link.node(), id, vertices, edges, idGen);
                List<GraphProperty> linkProps = toGraphProperties(link.properties(), Set.of());
                edges.add(new GraphEdge(id + "-" + childId, id, childId, "", link.toolTip(), edgeStyle(link), linkProps));
            }

            return id;
        }

        private String edgeStyle(NodeLink link)
        {
            int rowCount = link.getRowCount();
            // CSOFF
            boolean zeroRowCount = rowCount <= 0;
            // CSON
            int thickness = 0;
            while (rowCount > 0)
            {
                thickness++;
                rowCount /= 10;
            }
            if (thickness == 0)
            {
                thickness++;
            }

            String stroke = "#000000";
            if (UiUtils.isDarkLookAndFeel())
            {
                stroke = Constants.DARK_THEME_LIGHT_COLOR_HEX;
            }

            return String.format("rounded=true;edgeStyle=entityRelationEdgeStyle;dashed=%s;strokeColor=%s;startArrow=block;endArrow=none;strokeWidth=%d;", zeroRowCount ? "true"
                    : "false", stroke, thickness);
        }

        private List<GraphProperty> toGraphProperties(List<NodeProperty> nodeProps, Set<String> excludeNames)
        {
            List<GraphProperty> result = new ArrayList<>();
            for (NodeProperty p : nodeProps)
            {
                if (excludeNames.contains(p.name()
                        .toLowerCase()))
                {
                    continue;
                }
                List<GraphProperty> subProps = toGraphProperties(p.subProperties(), Set.of());
                result.add(new GraphProperty(p.category(), p.name(), p.description(), p.value(), p.includeInToolTip(), subProps));
            }
            return result;
        }
    }

    private static BufferedImage toImage(String resource)
    {
        try
        {
            return ImageIO.read(QueryPlanOutputComponent.class.getResourceAsStream(resource));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
