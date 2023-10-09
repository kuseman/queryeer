package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.stream.Collectors.toMap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.extensions.engine.IQueryEngine.IState.MetaParameter;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.ActionTarget;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.ActionType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.QueryAction;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.QueryActionOverride;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.QueryActionQuery;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.QueryActions;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectType;

class QueryActionsConfigurableTest
{
    static void main(String[] args) throws StreamReadException, DatabindException, IOException
    {

        try
        {
            // Set the look and feel to native (system LAF)
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        JFrame frame = new JFrame("");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600); // Wider frame for better layout

        QueryActions actions = new ObjectMapper().readValue(QueryActionsConfigurable.class.getResourceAsStream("/se/kuseman/payloadbuilder/catalog/jdbc/com.queryeer.jdbc.QueryActions.cfg"),
                QueryActions.class);

        actions.getQueries()
                .sort(Comparator.comparing(QueryActionQuery::getName));

        init(actions);

        // QueryActions actions = new QueryActions();
        // actions.rules.add(new QueryActionRule());
        // actions.actions.add(new QueryAction());

        frame.add(new QueryActionsConfigurableComponent(actions));
        frame.setVisible(true);

        // JPanel mainPanel = new JPanel();
        // mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
    }

    private static void init(QueryActions queryActions)
    {
        // Map and distribute rules to actions
        Map<String, QueryActionQuery> map = queryActions.getQueries()
                .stream()
                .collect(toMap(QueryActionQuery::getName, Function.identity()));

        List<QueryAction> queue = new ArrayList<>(queryActions.getActions());
        while (!queue.isEmpty())
        {
            QueryAction action = queue.remove(0);

            action.setQuery(map.get(action.getQueryName()));

            for (QueryActionOverride override : action.getOverrides())
            {
                override.setQuery(map.get(override.getQueryName()));
            }

            queue.addAll(action.getSubItems());
        }
    }

    static class QueryActionsConfigurableComponent extends JPanel
    {
        private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        private final DefaultMutableTreeNode queries = new DefaultMutableTreeNode();
        private final DefaultMutableTreeNode actions = new DefaultMutableTreeNode();
        private final QueryActions queryActions;
        private final JTree tree;
        private final DefaultTreeModel treeModel;
        private final JPopupMenu contextPopup = new JPopupMenu();

        QueryActionsConfigurableComponent(QueryActions queryActions)
        {
            this.queryActions = queryActions;
            setLayout(new BorderLayout());

            JPanel noSelectionPanel = new JPanel();
            noSelectionPanel.setLayout(new BorderLayout());
            noSelectionPanel.add(new JLabel("<html>Select an action or query for edit. Connect queries to actions."), BorderLayout.NORTH);

            JSplitPane splitPane = new JSplitPane();
            splitPane.setRightComponent(noSelectionPanel);
            splitPane.setDividerLocation(0.3d);

            tree = new JTree();
            tree.setCellRenderer(new DefaultTreeCellRenderer()
            {
                @Override
                public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
                {
                    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

                    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
                    Object node = treeNode.getUserObject();

                    if (value == actions)
                    {
                        setText("Actions");
                    }
                    else if (value == queries)
                    {
                        setText("Queries");
                    }
                    else if (node instanceof QueryAction action)
                    {
                        setIcon(treeNode.getChildCount() == 0 ? getLeafIcon()
                                : (expanded ? getOpenIcon()
                                        : getClosedIcon()));
                        setText(action.getTitle() + (action.getActionTypes()
                                .isEmpty() ? ""
                                        : (" " + action.getActionTypes())));
                    }
                    else if (node instanceof QueryActionQuery query)
                    {
                        setText(query.getName());
                    }

                    return this;
                }
            });
            tree.addMouseListener(mouseListener);
            tree.getSelectionModel()
                    .setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
            tree.getSelectionModel()
                    .addTreeSelectionListener(new TreeSelectionListener()
                    {
                        @Override
                        public void valueChanged(TreeSelectionEvent e)
                        {
                            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.getPath()
                                    .getLastPathComponent();
                            splitPane.setRightComponent(null);
                            if (node != null)
                            {
                                Object value = node.getUserObject();
                                if (value instanceof QueryActionQuery query)
                                {
                                    splitPane.setRightComponent(new QueryPanel(query, node, treeModel));
                                }
                                else if (value instanceof QueryAction action)
                                {
                                    splitPane.setRightComponent(new ActionPanel(action, node, treeModel, queries));
                                }
                            }

                            if (splitPane.getRightComponent() == null)
                            {
                                splitPane.setRightComponent(noSelectionPanel);
                            }

                            splitPane.setDividerLocation(0.3d);
                        }
                    });

            buildTree();

            splitPane.setLeftComponent(tree);

            treeModel = new DefaultTreeModel(root, true);
            tree.setModel(treeModel);
            tree.setRootVisible(false);
            tree.setShowsRootHandles(true);
            for (int i = 0; i < tree.getRowCount(); i++)
            {
                tree.expandRow(i);
            }

            add(splitPane);
        }

        private void buildTree()
        {
            List<Pair<DefaultMutableTreeNode, QueryAction>> queue = new ArrayList<>();
            for (QueryAction action : queryActions.getActions())
            {
                queue.add(Pair.of(actions, action));
            }
            while (queue.size() > 0)
            {
                Pair<DefaultMutableTreeNode, QueryAction> pair = queue.remove(0);

                DefaultMutableTreeNode parent = new DefaultMutableTreeNode(pair.getValue());
                parent.setAllowsChildren(true);
                pair.getKey()
                        .add(parent);

                for (QueryAction child : pair.getValue()
                        .getSubItems())
                {
                    queue.add(Pair.of(parent, child));
                }
            }
            root.add(actions);

            for (QueryActionQuery query : queryActions.getQueries())
            {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(query);
                node.setAllowsChildren(false);
                queries.add(node);
            }
            root.add(queries);
        }

        private MouseAdapter mouseListener = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (SwingUtilities.isRightMouseButton(e))
                {
                    TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                    if (selPath != null)
                    {
                        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) selPath.getLastPathComponent();
                        MutableTreeNode parentNode = (MutableTreeNode) treeNode.getParent();
                        Object value = treeNode.getUserObject();

                        int row = tree.getClosestRowForLocation(e.getX(), e.getY());
                        tree.setSelectionRow(row);

                        contextPopup.removeAll();

                        if (treeNode == actions)
                        {
                            contextPopup.add(new AddActionAction(treeNode, selPath));
                        }
                        else if (value instanceof QueryAction)
                        {
                            contextPopup.add(new AddActionAction(treeNode, selPath));
                            contextPopup.add(new DeleteActionAction(treeNode));

                            int index = treeModel.getIndexOfChild(treeNode.getParent(), treeNode);

                            if (index > 0)
                            {
                                contextPopup.add(new AbstractAction("Move up")
                                {
                                    @Override
                                    public void actionPerformed(ActionEvent e)
                                    {
                                        treeModel.removeNodeFromParent(treeNode);
                                        treeModel.insertNodeInto(treeNode, parentNode, index - 1);
                                        tree.setSelectionRow(row - 1);
                                    }
                                });
                            }

                            if (index < treeModel.getChildCount(treeNode.getParent()) - 1)
                            {
                                contextPopup.add(new AbstractAction("Move down")
                                {
                                    @Override
                                    public void actionPerformed(ActionEvent e)
                                    {
                                        treeModel.removeNodeFromParent(treeNode);
                                        treeModel.insertNodeInto(treeNode, parentNode, index + 1);
                                        tree.setSelectionRow(row + 1);
                                    }
                                });
                            }

                        }
                        else if (treeNode == queries)
                        {
                            contextPopup.add(new AddQueryAction(treeNode, selPath));
                        }
                        else if (value instanceof QueryActionQuery)
                        {
                            contextPopup.add(new DeleteQueryAction(treeNode));
                        }

                        contextPopup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        };

        private class AddActionAction extends AbstractAction
        {
            private final DefaultMutableTreeNode parent;
            private final TreePath path;

            AddActionAction(DefaultMutableTreeNode parent, TreePath path)
            {
                super("Add action");
                this.parent = parent;
                this.path = path;
            }

            @Override
            public void actionPerformed(ActionEvent e)
            {
                QueryAction newAction = new QueryAction();
                newAction.setTitle("New action");

                DefaultMutableTreeNode node = new DefaultMutableTreeNode(newAction, true);
                parent.add(node);

                treeModel.nodesWereInserted(parent, new int[] { parent.getChildCount() - 1 });

                if (tree.isCollapsed(path))
                {
                    tree.expandPath(path);
                }
            }
        }

        private class AddQueryAction extends AbstractAction
        {
            private final DefaultMutableTreeNode parent;
            private final TreePath path;

            AddQueryAction(DefaultMutableTreeNode parent, TreePath path)
            {
                super("Add query");
                this.parent = parent;
                this.path = path;
            }

            @Override
            public void actionPerformed(ActionEvent e)
            {
                QueryActionQuery newQuery = new QueryActionQuery();
                newQuery.setName("Query_" + (parent.getChildCount() + 1));

                DefaultMutableTreeNode node = new DefaultMutableTreeNode(newQuery, false);
                parent.add(node);

                treeModel.nodesWereInserted(parent, new int[] { parent.getChildCount() - 1 });

                if (tree.isCollapsed(path))
                {
                    tree.expandPath(path);
                }
            }
        }

        private class DeleteQueryAction extends AbstractAction
        {
            private final DefaultMutableTreeNode node;

            DeleteQueryAction(DefaultMutableTreeNode node)
            {
                super("Delete query");
                this.node = node;
            }

            @Override
            public void actionPerformed(ActionEvent e)
            {
                treeModel.removeNodeFromParent(node);
            }
        }

        private class DeleteActionAction extends AbstractAction
        {
            private final DefaultMutableTreeNode node;

            DeleteActionAction(DefaultMutableTreeNode node)
            {
                super("Delete action");
                this.node = node;
            }

            @Override
            public void actionPerformed(ActionEvent e)
            {
                treeModel.removeNodeFromParent(node);
            }
        }
    }

    static class ActionPanel extends JPanel
    {
        ActionPanel(QueryAction action, TreeNode node, DefaultTreeModel model, DefaultMutableTreeNode queries)
        {
            setLayout(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JLabel("Title:"), gbc);

            JTextField tite = new JTextField();
            tite.setText(action.getTitle());
            tite.getDocument()
                    .addDocumentListener(new DocumentListener()
                    {
                        @Override
                        public void removeUpdate(DocumentEvent e)
                        {
                            update();
                        }

                        @Override
                        public void insertUpdate(DocumentEvent e)
                        {
                            update();
                        }

                        @Override
                        public void changedUpdate(DocumentEvent e)
                        {
                            update();
                        }

                        private void update()
                        {
                            action.setTitle(tite.getText());
                            model.nodeChanged(node);
                        }
                    });

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0d;
            gbc.insets = new Insets(2, 2, 2, 2);
            gbc.gridwidth = 2;
            add(tite, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JLabel("Output: "), gbc);

            JComboBox<OutputType> outputCombo = new JComboBox<>(Arrays.stream(OutputType.values())
                    .filter(OutputType::isInteractive)
                    .toArray(OutputType[]::new));
            outputCombo.setSelectedItem(action.getOutput());
            outputCombo.addActionListener(l ->
            {
                action.setOutputType((OutputType) outputCombo.getSelectedItem());
                // notifyDirtyStateConsumers();
            });

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 1;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 2, 2, 2);
            gbc.gridwidth = 2;
            add(outputCombo, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JLabel("Query: "), gbc);

            JComboBox<QueryActionQuery> queryCombo = createQueryCombo(queries, true, query ->
            {
                action.setQuery(query);
                if (query == null)
                {
                    action.setQueryName(null);
                }
                else
                {
                    action.setQueryName(query.getName());
                }
            });
            queryCombo.setSelectedItem(action.getQuery());

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(queryCombo, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JLabel("Action Targets:"), gbc);

            JPanel actionTargetsPanel = new JPanel();
            actionTargetsPanel.setLayout(new BoxLayout(actionTargetsPanel, BoxLayout.Y_AXIS));
            actionTargetsPanel.setBackground(Color.WHITE);

            for (ActionTarget target : ActionTarget.values())
            {
                @SuppressWarnings("deprecation")
                JCheckBox checkBox = new JCheckBox(WordUtils.capitalizeFully(target.name()));
                checkBox.setBackground(Color.WHITE);
                checkBox.setSelected(action.getActionTargets()
                        .contains(target));
                checkBox.addActionListener(l ->
                {
                    if (checkBox.isSelected())
                    {
                        action.getActionTargets()
                                .add(target);
                    }
                    else
                    {
                        action.getActionTargets()
                                .remove(target);
                    }
                });
                actionTargetsPanel.add(checkBox);
            }

            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(2, 2, 2, 2);
            gbc.weightx = 0.33d;
            gbc.weighty = 0.6d;
            add(new JScrollPane(actionTargetsPanel), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 3;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JLabel("Action Types:"), gbc);

            JPanel actionTypesPanel = new JPanel();
            actionTypesPanel.setBackground(Color.WHITE);
            actionTypesPanel.setLayout(new BoxLayout(actionTypesPanel, BoxLayout.Y_AXIS));

            for (ActionType type : ActionType.values())
            {
                @SuppressWarnings("deprecation")
                JCheckBox checkBox = new JCheckBox(WordUtils.capitalizeFully(type.name()));
                checkBox.setToolTipText(type.getTooltip());
                checkBox.setBackground(Color.WHITE);
                checkBox.setSelected(action.getActionTypes()
                        .contains(type));
                checkBox.addActionListener(l ->
                {
                    if (checkBox.isSelected())
                    {
                        action.getActionTypes()
                                .add(type);
                    }
                    else
                    {
                        action.getActionTypes()
                                .remove(type);
                    }
                });
                actionTypesPanel.add(checkBox);
            }

            gbc.gridx = 1;
            gbc.gridy = 4;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 0.33d;
            gbc.weighty = 0.6d;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JScrollPane(actionTypesPanel), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 2;
            gbc.gridy = 3;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JLabel("Object Types:"), gbc);

            JPanel objectTypesPanel = new JPanel();
            objectTypesPanel.setBackground(Color.WHITE);
            objectTypesPanel.setLayout(new BoxLayout(objectTypesPanel, BoxLayout.Y_AXIS));

            for (ObjectType type : ObjectType.values())
            {
                @SuppressWarnings("deprecation")
                JCheckBox checkBox = new JCheckBox(WordUtils.capitalizeFully(type.name()));
                checkBox.setBackground(Color.WHITE);
                checkBox.setSelected(action.getObjectTypes()
                        .contains(type));
                checkBox.addActionListener(l ->
                {
                    if (checkBox.isSelected())
                    {
                        action.getObjectTypes()
                                .add(type);
                    }
                    else
                    {
                        action.getObjectTypes()
                                .remove(type);
                    }
                });
                objectTypesPanel.add(checkBox);
            }

            gbc.gridx = 2;
            gbc.gridy = 4;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 0.33d;
            gbc.weighty = 0.6d;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JScrollPane(objectTypesPanel), gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 5;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JLabel("Overrides: "), gbc);

            JList<QueryActionOverride> overrides = new JList<QueryActionOverride>()
            {
                @Override
                public String getToolTipText(MouseEvent e)
                {
                    int index = locationToIndex(e.getPoint());
                    if (index > -1)
                    {
                        return getModel().getElementAt(index)
                                .toString();
                    }
                    return null;
                }
            };
            overrides.setCellRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                    QueryActionOverride override = (QueryActionOverride) value;
                    String text = StringUtils.defaultIfBlank(override.getRule(), "Rule");
                    label.setText(text);

                    return label;
                }
            });

            QueryActionOverride prototype = new QueryActionOverride();
            prototype.setRule("some rule expression");
            overrides.setPrototypeCellValue(prototype);
            overrides.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            DefaultListModel<QueryActionOverride> overridesModel = new DefaultListModel<>();
            for (QueryActionOverride override : action.getOverrides())
            {
                overridesModel.addElement(override);
            }
            overrides.setModel(overridesModel);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 6;
            gbc.gridwidth = 3;
            gbc.weightx = 1.0d;
            gbc.weighty = 0.4d;
            gbc.fill = GridBagConstraints.BOTH;
            add(new JScrollPane(overrides), gbc);

            JButton addOverrideButton = new JButton("+");
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 7;
            gbc.anchor = GridBagConstraints.WEST;
            add(addOverrideButton, gbc);

            JButton deleteOverrideButton = new JButton("-");
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 7;
            gbc.anchor = GridBagConstraints.SOUTH;
            add(deleteOverrideButton, gbc);

            JButton editOverrideButton = new JButton("...");
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 7;
            gbc.anchor = GridBagConstraints.EAST;
            add(editOverrideButton, gbc);

            addOverrideButton.addActionListener(l ->
            {
                QueryActionOverride override = new QueryActionOverride();
                override.setRule("Rule " + overridesModel.size());
                overridesModel.addElement(override);
                action.getOverrides()
                        .add(override);
                // if (notify)
                // {
                // notifyDirtyStateConsumers();
                // }
            });
            deleteOverrideButton.addActionListener(l ->
            {
                if (overrides.getSelectedValue() != null)
                {
                    QueryActionOverride override = overrides.getSelectedValue();
                    overridesModel.removeElement(override);
                    action.getOverrides()
                            .add(override);
                    // if (notify)
                    // {
                    // notifyDirtyStateConsumers();
                    // }
                }
            });
            editOverrideButton.addActionListener(l ->
            {
                if (overrides.getSelectedValue() != null)
                {
                    openOverrideDialog(overrides.getSelectedValue(), queries);
                    overrides.repaint();
                }
            });
            overrides.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (e.getClickCount() == 2
                            && SwingUtilities.isLeftMouseButton(e))
                    {
                        int index = overrides.locationToIndex(e.getPoint());
                        if (index >= 0)
                        {
                            openOverrideDialog(overridesModel.getElementAt(index), queries);
                            overrides.repaint();
                        }
                    }
                };
            });
        }
    }

    static void openOverrideDialog(QueryActionOverride override, DefaultMutableTreeNode queries)
    {
        // Create the override dialog
        JDialog dialog = new JDialog();
        dialog.setModal(true);
        dialog.setTitle("Override: " + override.getRule());
        dialog.setSize(800, 600);
        dialog.setLayout(new GridBagLayout());
        // Close dialog on escape
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getRootPane()
                .registerKeyboardAction(new ActionListener()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        dialog.setVisible(false);
                    }
                }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        dialog.add(new JLabel("Query:"), gbc);

        JComboBox<QueryActionQuery> queryCombo = createQueryCombo(queries, true, query ->
        {
            override.setQuery(query);
            if (query == null)
            {
                override.setQueryName(null);
            }
            else
            {
                override.setQueryName(query.getName());
            }
        });
        queryCombo.setSelectedItem(override.getQuery());

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        dialog.add(queryCombo, gbc);

        JLabel ruleLabel = new JLabel("Rule:");
        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 1;
        dialog.add(ruleLabel, gbc);

        String message = "<html>";
        //@formatter:off
        message += "<h4>Payloadbuilder expression</h4>"
                + "First override whos rule evaluates to true will be used.<br/>"
                + "Parameters are accessiable via <b>@param</b>-notion:<br/>";
        
        List<MetaParameter> meta = List.of(new MetaParameter("url", null, ""),
                new MetaParameter("database", null, ""));
        message += "<ul>";
        for (MetaParameter entry : meta)
        {
            message += "<li><b>" + entry.name() + "</b> - " + entry.description() + "</li>";
        }
        message += "</ul>";
        message += "</html>";
        //@formatter:on
        ruleLabel.setToolTipText(message);

        JTextArea ruleField = new JTextArea(override.getRule());
        ruleField.setRows(3);
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0d;
        gbc.weighty = 1.0d;
        ruleField.getDocument()
                .addDocumentListener(new DocumentListener()
                {
                    @Override
                    public void removeUpdate(DocumentEvent e)
                    {
                        update();
                    }

                    @Override
                    public void insertUpdate(DocumentEvent e)
                    {
                        update();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e)
                    {
                        update();
                    }

                    private void update()
                    {
                        override.setRule(ruleField.getText());
                    }
                });
        JScrollPane scroll = new JScrollPane(ruleField);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setWheelScrollingEnabled(true);
        dialog.add(scroll, gbc);

        dialog.setVisible(true);
    }

    static class QueryPanel extends JPanel
    {
        QueryPanel(QueryActionQuery actionQuery, TreeNode node, DefaultTreeModel model)
        {
            setLayout(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JLabel("Name:"), gbc);

            JTextField queryName = new JTextField();
            queryName.setText(actionQuery.getName());
            queryName.getDocument()
                    .addDocumentListener(new DocumentListener()
                    {
                        @Override
                        public void removeUpdate(DocumentEvent e)
                        {
                            update();
                        }

                        @Override
                        public void insertUpdate(DocumentEvent e)
                        {
                            update();
                        }

                        @Override
                        public void changedUpdate(DocumentEvent e)
                        {
                            update();
                        }

                        private void update()
                        {
                            actionQuery.setName(queryName.getText());
                            model.nodeChanged(node);
                        }
                    });
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weightx = 1.0d;
            gbc.insets = new Insets(2, 2, 2, 2);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            add(queryName, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JLabel("Query:"), gbc);

            JTextArea query = new JTextArea();
            query.setRows(7);
            query.setText(actionQuery.getQuery());
            query.getDocument()
                    .addDocumentListener(new DocumentListener()
                    {
                        @Override
                        public void removeUpdate(DocumentEvent e)
                        {
                            update();
                        }

                        @Override
                        public void insertUpdate(DocumentEvent e)
                        {
                            update();
                        }

                        @Override
                        public void changedUpdate(DocumentEvent e)
                        {
                            update();
                        }

                        private void update()
                        {
                            actionQuery.setQuery(query.getText());
                        }
                    });

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.weightx = 1.0d;
            gbc.weighty = 1.0d;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(new JScrollPane(query), gbc);
        }
    }

    static JComboBox<QueryActionQuery> createQueryCombo(DefaultMutableTreeNode queries, boolean addEmptyNode, Consumer<QueryActionQuery> consumer)
    {
        DefaultComboBoxModel<QueryActionQuery> model = new DefaultComboBoxModel<QueryActionQuery>();
        JComboBox<QueryActionQuery> combo = new JComboBox<>(model);

        QueryActionQuery empty = new QueryActionQuery();
        if (addEmptyNode)
        {
            model.addElement(empty);
        }

        int count = queries.getChildCount();
        for (int i = 0; i < count; i++)
        {
            model.addElement((QueryActionQuery) ((DefaultMutableTreeNode) queries.getChildAt(i)).getUserObject());
        }

        combo.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                QueryActionQuery query = (QueryActionQuery) combo.getSelectedItem();
                consumer.accept(query == empty ? null
                        : query);
            }
        });
        combo.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                QueryActionQuery query = (QueryActionQuery) value;
                if (query != null)
                {
                    setText(query.getName());
                }
                return this;
            }
        });

        return combo;
    }
}
