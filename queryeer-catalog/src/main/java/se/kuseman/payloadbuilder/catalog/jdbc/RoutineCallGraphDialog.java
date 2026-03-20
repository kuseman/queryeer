package se.kuseman.payloadbuilder.catalog.jdbc;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.queryeer.api.component.ADocumentListenerAdapter;
import com.queryeer.api.component.AnimatedIcon;
import com.queryeer.api.component.DialogUtils.ADialog;
import com.queryeer.api.extensions.visualization.graph.Graph;
import com.queryeer.api.service.IGraphVisualizationService;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Routine;

/**
 * Non-modal dialog combining routine selection (left) and the live call graph (right) in a single split pane. The user can refine the selection and rebuild the graph without reopening the dialog.
 */
class RoutineCallGraphDialog extends ADialog
{
    private final String database;
    private final Catalog catalog;
    private final JdbcDialect dialect;
    private final IConnectionContext connectionContext;
    private final IGraphVisualizationService graphVisualizationService;

    private final List<Routine> allRoutines;
    private final Map<String, JCheckBox> checkboxByKey = new LinkedHashMap<>();

    private JTextField filterField;
    private JPanel listPanel;
    private JButton buildButton;

    /** Right-hand container — content is swapped between placeholder, spinner, and the graph component. */
    private JPanel graphContainer;

    // CSOFF
    RoutineCallGraphDialog(String database, Catalog catalog, JdbcDialect dialect, IConnectionContext connectionContext, IGraphVisualizationService graphVisualizationService)
    {
        super((Frame) null, database + " — Routine Call Graph", false);
        this.database = database;
        this.catalog = catalog;
        this.dialect = dialect;
        this.connectionContext = connectionContext;
        this.graphVisualizationService = graphVisualizationService;
        this.allRoutines = catalog.getRoutines()
                .stream()
                .sorted(Comparator.comparing(RoutineCallGraphDialog::routineKey))
                .collect(Collectors.toList());
        initUI();
        rebuildList("");
    }
    // CSON

    private void initUI()
    {
        setPreferredSize(new Dimension(1200, 750));

        // ── Left panel: filter + checkbox list + action buttons ──────────────
        JPanel filterRow = new JPanel(new BorderLayout(6, 0));
        filterRow.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));
        filterField = new JTextField();
        filterField.getDocument()
                .addDocumentListener(new ADocumentListenerAdapter()
                {
                    @Override
                    protected void update()
                    {
                        rebuildList(filterField.getText()
                                .trim());
                    }
                });
        filterRow.add(new JLabel("Filter:"), BorderLayout.WEST);
        filterRow.add(filterField, BorderLayout.CENTER);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        JButton selectAll = new JButton("Select All");
        selectAll.addActionListener(e -> setVisibleChecked(true));
        JButton deselectAll = new JButton("Deselect All");
        deselectAll.addActionListener(e -> setVisibleChecked(false));
        JPanel selectionButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        selectionButtons.add(selectAll);
        selectionButtons.add(deselectAll);

        buildButton = new JButton("Build Graph");
        buildButton.setEnabled(false);
        buildButton.addActionListener(e -> buildGraph());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        actionButtons.add(closeButton);
        actionButtons.add(buildButton);

        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        buttonRow.add(selectionButtons, BorderLayout.WEST);
        buttonRow.add(actionButtons, BorderLayout.EAST);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(filterRow, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(listPanel), BorderLayout.CENTER);
        leftPanel.add(buttonRow, BorderLayout.SOUTH);

        // ── Right panel: starts with a hint label, replaced by graph on build ─
        graphContainer = new JPanel(new BorderLayout());
        showPlaceholder();

        // ── Split pane ────────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, graphContainer);
        split.setDividerLocation(320);
        split.setOneTouchExpandable(true);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(split, BorderLayout.CENTER);
        pack();
    }

    private void showPlaceholder()
    {
        JLabel hint = new JLabel("Select routines and click \"Build Graph\"", SwingConstants.CENTER);
        hint.setFont(hint.getFont()
                .deriveFont(Font.ITALIC));
        graphContainer.removeAll();
        graphContainer.add(hint, BorderLayout.CENTER);
        graphContainer.revalidate();
        graphContainer.repaint();
    }

    private void showSpinner()
    {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        p.add(new JLabel(AnimatedIcon.createSmallSpinner()));
        p.add(new JLabel("Building graph…"));
        graphContainer.removeAll();
        graphContainer.add(p, BorderLayout.CENTER);
        graphContainer.revalidate();
        graphContainer.repaint();
    }

    private void rebuildList(String filter)
    {
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (Map.Entry<String, JCheckBox> e : checkboxByKey.entrySet())
        {
            states.put(e.getKey(), e.getValue()
                    .isSelected());
        }

        checkboxByKey.clear();
        listPanel.removeAll();

        String lowerFilter = filter.toLowerCase();
        for (Routine r : allRoutines)
        {
            String key = routineKey(r);
            String label = routineLabel(r);
            if (!lowerFilter.isEmpty()
                    && !label.toLowerCase()
                            .contains(lowerFilter))
            {
                continue;
            }

            JCheckBox cb = new JCheckBox(label);
            cb.setSelected(states.getOrDefault(key, false));
            cb.addItemListener(e -> updateBuildButton());
            checkboxByKey.put(key, cb);

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
            row.add(cb);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            listPanel.add(row);
        }
        listPanel.add(Box.createVerticalGlue());
        listPanel.revalidate();
        listPanel.repaint();
        updateBuildButton();
    }

    private void setVisibleChecked(boolean checked)
    {
        checkboxByKey.values()
                .forEach(cb -> cb.setSelected(checked));
        updateBuildButton();
    }

    private void updateBuildButton()
    {
        boolean anyChecked = checkboxByKey.values()
                .stream()
                .anyMatch(JCheckBox::isSelected);
        buildButton.setEnabled(anyChecked);
    }

    private void buildGraph()
    {
        List<Routine> selected = new ArrayList<>();
        for (Routine r : allRoutines)
        {
            JCheckBox cb = checkboxByKey.get(routineKey(r));
            if (cb != null
                    && cb.isSelected())
            {
                selected.add(r);
            }
        }
        if (selected.isEmpty())
        {
            return;
        }

        buildButton.setEnabled(false);
        showSpinner();

        JdbcQueryEngine.EXECUTOR.execute(() ->
        {
            Graph graph = RoutineCallGraph.buildGraph(database, selected, catalog, dialect, connectionContext);
            SwingUtilities.invokeLater(() ->
            {
                JComponent graphComponent = graphVisualizationService.createGraphComponent(graph);
                if (graphComponent != null)
                {
                    graphContainer.removeAll();
                    graphContainer.add(graphComponent, BorderLayout.CENTER);
                }
                else
                {
                    // Fallback: open in separate window and restore placeholder
                    graphVisualizationService.showGraph(graph);
                    showPlaceholder();
                }
                graphContainer.revalidate();
                graphContainer.repaint();
                updateBuildButton();
            });
        });
    }

    static String routineKey(Routine r)
    {
        return isBlank(r.getSchema()) ? r.getName()
                .toLowerCase()
                : r.getSchema()
                        .toLowerCase()
                  + "."
                  + r.getName()
                          .toLowerCase();
    }

    private static String routineLabel(Routine r)
    {
        String name = isBlank(r.getSchema()) ? r.getName()
                : r.getSchema() + "." + r.getName();
        return name + "  ["
               + r.getType()
                       .name()
               + "]";
    }
}
