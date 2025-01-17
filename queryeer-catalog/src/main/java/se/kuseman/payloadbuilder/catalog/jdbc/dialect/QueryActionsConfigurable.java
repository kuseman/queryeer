package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.component.ADocumentListenerAdapter;
import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.editor.IEditorFactory;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.editor.ITextEditorKit;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.engine.IQueryEngine.IState.MetaParameter;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.IExpressionEvaluator;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IIconFactory.Provider;

import se.kuseman.payloadbuilder.api.expression.IExpression;
import se.kuseman.payloadbuilder.catalog.jdbc.Constants;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionState;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectType;

/**
 * Configurable for query actions
 * 
 * <pre>
 * - Link actions (CTRL-hover/click) in connection tree or in text editor
 * - Context menu (right click) in connection tree
 * </pre>
 */
class QueryActionsConfigurable implements IConfigurable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryActionsConfigurable.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String NAME = "com.queryeer.jdbc.QueryActions";
    private static final String NAME_DEFAULT = "com.queryeer.jdbc.QueryActions.default";

    private final IConfig config;
    private final IExpressionEvaluator expressionEvaluator;
    private final IEditorFactory editorFactory;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();
    private final Icon infoIcon;

    private QueryActionsConfigurableComponent component;
    private QueryActions queryActions;

    QueryActionsConfigurable(IConfig config, IExpressionEvaluator expressionEvaluator, IEditorFactory editorFactory, IIconFactory iconFactory)
    {
        this.config = requireNonNull(config, "config");
        this.expressionEvaluator = requireNonNull(expressionEvaluator, "expressionEvaluator");
        this.editorFactory = requireNonNull(editorFactory, "editorFactory");
        infoIcon = requireNonNull(iconFactory).getIcon(Provider.FONTAWESOME, "INFO");
        load();
    }

    @Override
    public String getTitle()
    {
        return "Query Actions";
    }

    @Override
    public String groupName()
    {
        return Constants.TITLE;
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new QueryActionsConfigurableComponent();
            component.init(queryActions);
        }
        return component;
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyStateConsumers.add(consumer);
    }

    @Override
    public void removeDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyStateConsumers.remove(consumer);
    }

    @Override
    public void commitChanges()
    {
        QueryActions queryActions = component.getQueryActions()
                .clone();
        init(queryActions, true);

        File file = config.getConfigFileName(NAME);
        try
        {
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(file, queryActions);
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(component, "Error saving config, message: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        this.queryActions = queryActions;
    }

    @Override
    public void revertChanges()
    {
        component.init(queryActions);
    }

    /** Return query actions for provided database type */
    List<QueryActionResult> getQueryActions(String url, String database, ActionTarget target, ActionType type, Set<ObjectType> objectTypes)
    {
        return getQueryActions(queryActions.actions, url, database, target, type, objectTypes);
    }

    /** Return query actions for provided database type */
    private List<QueryActionResult> getQueryActions(List<QueryAction> actions, String url, String database, ActionTarget target, ActionType type, Set<ObjectType> objectTypes)
    {
        Map<String, Object> expressionParams = Map.of("url", url, "database", database);

        List<QueryActionResult> result = new ArrayList<>();
        for (QueryAction action : actions)
        {
            boolean hasSubItems = !action.getSubItems()
                    .isEmpty();

            if (!hasSubItems
                    && (!action.getActionTargets()
                            .contains(target)
                            || !action.getActionTypes()
                                    .contains(type)
                            || !CollectionUtils.containsAny(objectTypes, action.getObjectTypes())))
            {
                continue;
            }

            String title = action.getTitle();
            OutputType output = action.getOutput();
            String query = action.getQuery() != null ? action.getQuery()
                    .getQuery()
                    : "";

            if (hasSubItems)
            {
                List<QueryActionResult> subItems = getQueryActions(action.getSubItems(), url, database, target, type, objectTypes);
                if (!subItems.isEmpty())
                {
                    result.add(new QueryActionResult(title, "", output, subItems));
                }
            }
            else
            {
                for (QueryActionOverride override : action.getOverrides())
                {
                    if (override.ruleExpression == null
                            || override.query == null)
                    {
                        continue;
                    }

                    try
                    {
                        if (expressionEvaluator.evaluatePredicate(override.ruleExpression, expressionParams))
                        {
                            query = override.query.getQuery();
                            break;
                        }
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Error evaluating query action override expression: {}, for action: {} ", override.getRule(), action.getTitle(), e);
                    }
                }

                if (!isBlank(query))
                {
                    result.add(new QueryActionResult(title, query, output, emptyList()));
                }
            }
        }

        return result;
    }

    /** Resulting query action after rule evaluation etc. */
    record QueryActionResult(String title, String query, OutputType output, List<QueryActionResult> subItems)
    {
        boolean hasSubItems()
        {
            return !subItems.isEmpty();
        }
    }

    /*
     * @formatter:off
     * - database type
     * - object type
     * - action type
     * -- link
     * -- context
     * 
     * "databases": {
     *   "oracle": [
     *     {
     *       "name": "Top 500"
     *       "actionType": [ "LINK" ],
     *       "objectType": [ "TABLESOURCE" ],
     *       "query": [
     *         "",
     *         "",
     *         ""
     *       ]
     *     }
     *   ],
     *   "sqlserver": [
     *   
     *   ]
     * 
     * }
     * @formatter:on
     */

    private void load()
    {
        File file = config.getConfigFileName(NAME);
        File fileDefault = config.getConfigFileName(NAME_DEFAULT);

        // Always copy the latest built in config to default so the user have something to create a custom config from
        String builtInConfig = getBuiltInConfig();
        writeConfig(fileDefault, builtInConfig);

        // Load config file
        if (file.exists())
        {
            try
            {
                queryActions = MAPPER.readValue(file, QueryActions.class);
            }
            catch (IOException e)
            {
                LOGGER.error("Error loading config from: {}", file.getAbsolutePath(), e);
            }
        }
        // .. else use the built in config
        else
        {
            try
            {
                queryActions = MAPPER.readValue(builtInConfig, QueryActions.class);
            }
            catch (IOException e)
            {
                LOGGER.error("Error loading built in config", e);
            }
        }

        if (queryActions == null)
        {
            queryActions = new QueryActions();
        }

        init(queryActions, false);
    }

    private void notifyDirtyStateConsumers()
    {
        if (!component.notify)
        {
            return;
        }
        int size = dirtyStateConsumers.size();
        for (int i = size - 1; i >= 0; i--)
        {
            dirtyStateConsumers.get(i)
                    .accept(true);
        }
    }

    private String getBuiltInConfig()
    {
        try
        {
            return IOUtils.toString(QueryActionsConfigurable.class.getResourceAsStream("/se/kuseman/payloadbuilder/catalog/jdbc/com.queryeer.jdbc.QueryActions.cfg"), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return "";
        }
    }

    private void writeConfig(File file, String content)
    {
        try
        {
            FileUtils.write(file, content, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void init(QueryActions queryActions, boolean interactive)
    {
        // Map and distribute rules to actions
        Map<String, QueryActionQuery> map = queryActions.getQueries()
                .stream()
                .collect(toMap(QueryActionQuery::getName, Function.identity()));

        List<QueryAction> queue = new ArrayList<>(queryActions.getActions());
        while (!queue.isEmpty())
        {
            QueryAction action = queue.remove(0);

            if (!action.subItems.isEmpty())
            {
                action.output = null;
                action.actionTargets = new HashSet<>();
                action.actionTypes = new HashSet<>();
                action.objectTypes = new HashSet<>();
            }

            QueryActionQuery query = map.get(action.getQueryName());
            action.setQuery(query);

            for (QueryActionOverride override : action.getOverrides())
            {
                override.setQuery(map.get(override.getQueryName()));

                if (StringUtils.isNotBlank(override.getRule()))
                {
                    try
                    {
                        IExpression expression = expressionEvaluator.parse(override.getRule());
                        override.ruleExpression = expression;
                    }
                    catch (Exception e)
                    {
                        String message = "Error parsing expression: " + override.getRule() + " for action: " + action.getTitle();
                        if (interactive)
                        {
                            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
                        }

                        LOGGER.error(message, e);
                    }
                }
            }

            queue.addAll(action.getSubItems());
        }
    }

    /** Main configurable component. */
    class QueryActionsConfigurableComponent extends JPanel
    {
        private final JTree tree;
        private final JPopupMenu contextPopup = new JPopupMenu();
        private final JPanel noSelectionPanel;
        private final JSplitPane splitPane;

        private DefaultTreeModel treeModel;
        private DefaultMutableTreeNode root;
        private DefaultMutableTreeNode queries;
        private DefaultMutableTreeNode actions;
        private boolean notify = true;

        /** Init ui from query actions. */
        void init(QueryActions queryActions)
        {
            notify = false;

            // Connect the clones
            QueryActions clone = queryActions.clone();
            QueryActionsConfigurable.this.init(clone, true);

            buildTree(clone);
            splitPane.setRightComponent(noSelectionPanel);
            notify = true;
        }

        QueryActions getQueryActions()
        {
            int count = queries.getChildCount();
            List<QueryActionQuery> qs = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
            {
                qs.add((QueryActionQuery) (((DefaultMutableTreeNode) queries.getChildAt(i)).getUserObject()));
            }
            count = actions.getChildCount();
            List<QueryAction> as = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
            {
                as.add((QueryAction) (((DefaultMutableTreeNode) actions.getChildAt(i)).getUserObject()));
            }
            QueryActions result = new QueryActions();
            result.queries = qs;
            result.actions = as;
            return result;
        }

        QueryActionsConfigurableComponent()
        {
            setLayout(new BorderLayout());

            noSelectionPanel = new JPanel();
            noSelectionPanel.setLayout(new BorderLayout());
            noSelectionPanel.add(new JLabel("<html>Select an action or query for edit. Connect queries to actions."), BorderLayout.NORTH);

            splitPane = new JSplitPane();
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
                                    notify = false;
                                    splitPane.setRightComponent(new QueryPanel(query, node, treeModel));
                                    notify = true;
                                }
                                else if (value instanceof QueryAction action)
                                {
                                    notify = false;
                                    splitPane.setRightComponent(new ActionPanel(action, node, treeModel, queries));
                                    notify = true;
                                }
                            }

                            if (splitPane.getRightComponent() == null)
                            {
                                splitPane.setRightComponent(noSelectionPanel);
                            }

                            splitPane.setDividerLocation(0.3d);
                        }
                    });

            splitPane.setLeftComponent(tree);
            tree.setRootVisible(false);
            tree.setShowsRootHandles(true);

            add(splitPane);
        }

        private void buildTree(QueryActions queryActions)
        {
            root = new DefaultMutableTreeNode();
            queries = new DefaultMutableTreeNode();
            actions = new DefaultMutableTreeNode();
            root.add(actions);
            root.add(queries);

            treeModel = new DefaultTreeModel(root, true);

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

            for (QueryActionQuery query : queryActions.getQueries())
            {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(query);
                node.setAllowsChildren(false);
                queries.add(node);
            }

            tree.setModel(treeModel);

            for (int i = 0; i < tree.getRowCount(); i++)
            {
                tree.expandRow(i);
            }

            tree.setSelectionRow(0);
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
                                        notifyDirtyStateConsumers();
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
                                        notifyDirtyStateConsumers();
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
                QueryAction parentAction = parent.getUserObject() instanceof QueryAction qa ? qa
                        : null;
                QueryAction newAction = new QueryAction();
                newAction.setTitle("New action");
                if (parentAction != null)
                {
                    parentAction.getSubItems()
                            .add(newAction);
                }

                DefaultMutableTreeNode node = new DefaultMutableTreeNode(newAction, true);
                parent.add(node);

                treeModel.nodesWereInserted(parent, new int[] { parent.getChildCount() - 1 });

                if (tree.isCollapsed(path))
                {
                    tree.expandPath(path);
                }
                notifyDirtyStateConsumers();
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
                notifyDirtyStateConsumers();
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
                notifyDirtyStateConsumers();
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
                QueryAction parentAction = ((DefaultMutableTreeNode) node.getParent()).getUserObject() instanceof QueryAction qa ? qa
                        : null;
                if (parentAction != null)
                {
                    parentAction.getSubItems()
                            .remove(node.getUserObject());
                }
                treeModel.removeNodeFromParent(node);
                notifyDirtyStateConsumers();
            }
        }
    }

    class ActionPanel extends JPanel
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
                    .addDocumentListener(new ADocumentListenerAdapter()
                    {
                        @Override
                        protected void update()
                        {
                            action.setTitle(tite.getText());
                            model.nodeChanged(node);
                            notifyDirtyStateConsumers();
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
                notifyDirtyStateConsumers();
            });
            outputCombo.setEnabled(action.getSubItems()
                    .isEmpty());

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
                notifyDirtyStateConsumers();
            });
            queryCombo.setSelectedItem(action.getQuery());
            queryCombo.setEnabled(action.getSubItems()
                    .isEmpty());

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
            actionTargetsPanel.setEnabled(action.getSubItems()
                    .isEmpty());
            actionTargetsPanel.setLayout(new BoxLayout(actionTargetsPanel, BoxLayout.Y_AXIS));
            actionTargetsPanel.setBackground(Color.WHITE);

            for (ActionTarget target : ActionTarget.values())
            {
                @SuppressWarnings("deprecation")
                JCheckBox checkBox = new JCheckBox(WordUtils.capitalizeFully(target.name()));
                checkBox.setEnabled(action.getSubItems()
                        .isEmpty());
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
                    notifyDirtyStateConsumers();
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
            actionTypesPanel.setEnabled(action.getSubItems()
                    .isEmpty());
            actionTypesPanel.setBackground(Color.WHITE);
            actionTypesPanel.setLayout(new BoxLayout(actionTypesPanel, BoxLayout.Y_AXIS));

            for (ActionType type : ActionType.values())
            {
                @SuppressWarnings("deprecation")
                JCheckBox checkBox = new JCheckBox(WordUtils.capitalizeFully(type.name()));
                checkBox.setEnabled(action.getSubItems()
                        .isEmpty());
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
                    notifyDirtyStateConsumers();
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
                checkBox.setEnabled(action.getSubItems()
                        .isEmpty());
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
                    notifyDirtyStateConsumers();
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

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 7;
            gbc.anchor = GridBagConstraints.WEST;
            JButton addOverrideButton = new JButton("+");
            add(addOverrideButton, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 7;
            gbc.anchor = GridBagConstraints.SOUTH;
            JButton deleteOverrideButton = new JButton("-");
            add(deleteOverrideButton, gbc);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 7;
            gbc.anchor = GridBagConstraints.EAST;
            JButton editOverrideButton = new JButton("...");
            add(editOverrideButton, gbc);

            addOverrideButton.addActionListener(l ->
            {
                QueryActionOverride override = new QueryActionOverride();
                override.setRule("Rule " + overridesModel.size());
                overridesModel.addElement(override);
                action.getOverrides()
                        .add(override);
                notifyDirtyStateConsumers();
            });
            deleteOverrideButton.addActionListener(l ->
            {
                if (overrides.getSelectedValue() != null)
                {
                    QueryActionOverride override = overrides.getSelectedValue();
                    overridesModel.removeElement(override);
                    action.getOverrides()
                            .add(override);
                    notifyDirtyStateConsumers();
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

    void openOverrideDialog(QueryActionOverride override, DefaultMutableTreeNode queries)
    {
        component.notify = false;
        DialogUtils.ADialog dialog = new DialogUtils.ADialog();
        dialog.setModal(true);
        dialog.setTitle("Override: " + override.getRule());
        dialog.setSize(800, 600);
        dialog.setLayout(new GridBagLayout());
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

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
            notifyDirtyStateConsumers();
        });
        queryCombo.setSelectedItem(override.getQuery());

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        dialog.add(queryCombo, gbc);

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel ruleLabel = new JLabel("Rule:", infoIcon, SwingConstants.LEADING);
        dialog.add(ruleLabel, gbc);

        String message = "<html>";
        //@formatter:off
        message += "<h4>Payloadbuilder expression</h4>"
                + "First override whos rule evaluates to true will be used.<br/>"
                + "Parameters are accessiable via <b>@param</b>-notion:<br/>";
        List<MetaParameter> meta = IConnectionState.getMetaParameters("", "");
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
                .addDocumentListener(new ADocumentListenerAdapter()
                {
                    @Override
                    protected void update()
                    {
                        override.setRule(ruleField.getText());
                        notifyDirtyStateConsumers();
                    }
                });
        JScrollPane scroll = new JScrollPane(ruleField);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setWheelScrollingEnabled(true);
        dialog.add(scroll, gbc);
        component.notify = true;
        dialog.setVisible(true);
    }

    class QueryPanel extends JPanel
    {
        private static final ITextEditorKit SQL_EDITOR_KIT = new ITextEditorKit()
        {
            @Override
            public String getSyntaxMimeType()
            {
                return "text/sql";
            }
        };

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
                    .addDocumentListener(new ADocumentListenerAdapter()
                    {
                        @Override
                        protected void update()
                        {
                            actionQuery.setName(queryName.getText());
                            model.nodeChanged(node);
                            notifyDirtyStateConsumers();
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
            JLabel queryLabel = new JLabel("Query:", infoIcon, SwingConstants.LEADING);
            //@formatter:off
            String message = "<html>"
                    + "Query that will be executed when trigged by action.<br/>"
                    + "Different placeholders are available per object type:"
                    + "<ul>"
                    + "<li>" + ObjectType.TABLE_SOURCE_TYPES + " - ${catalog}/${schema}/${name}</li>"
                    + "<li>" + ObjectType.FUNCTION_OR_PROCEDURE_TYPES + " - ${catalog}/${schema}/${name}</li>"
                    + "<li>TRIGGER - ${name}</li>"
                    + "</ul>"
                    + "</html>";
            queryLabel.setToolTipText(message);
            add(queryLabel, gbc);

            ITextEditor query = editorFactory.createTextEditor(SQL_EDITOR_KIT);
            query.addPropertyChangeListener(p ->
            {
                actionQuery.setQuery(query.getValue(true));
                notifyDirtyStateConsumers();
            });
            query.setValue(actionQuery.getQuery());

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.weightx = 1.0d;
            gbc.weighty = 1.0d;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(2, 2, 2, 2);
            add(query.getComponent(), gbc);
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

    static class QueryActions
    {
        @JsonProperty
        private List<QueryActionQuery> queries = new ArrayList<>();

        @JsonProperty
        private List<QueryAction> actions = new ArrayList<>();

        public List<QueryActionQuery> getQueries()
        {
            return queries;
        }

        public void setQueries(List<QueryActionQuery> queries)
        {
            this.queries = queries;
        }

        public List<QueryAction> getActions()
        {
            return actions;
        }

        public void setActions(List<QueryAction> actions)
        {
            this.actions = actions;
        }

        @Override
        public QueryActions clone()
        {
            QueryActions result = new QueryActions();
            result.actions = new ArrayList<>(actions.stream()
                    .map(QueryAction::clone)
                    .toList());
            result.queries = new ArrayList<>(queries.stream()
                    .map(QueryActionQuery::clone)
                    .toList());
            return result;
        }
    }

    /** A query definition for an action query. */
    static class QueryActionQuery
    {
        @JsonProperty
        private String name = "";
        @JsonProperty
        private Object query = "";

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getQuery()
        {
            if (query == null)
            {
                return "";
            }
            if (query instanceof String)
            {
                return (String) query;
            }
            else if (query instanceof Collection)
            {
                return ((Collection<?>) query).stream()
                        .map(Object::toString)
                        .collect(joining(System.lineSeparator()));
            }
            return String.valueOf(query);
        }

        public void setQuery(Object query)
        {
            if (!(query instanceof Collection))
            {
                this.query = new ArrayList<>(asList(String.valueOf(query)
                        .split(System.lineSeparator())));
            }
            else
            {
                this.query = query;
            }
        }

        @Override
        public QueryActionQuery clone()
        {
            QueryActionQuery result = new QueryActionQuery();
            result.name = name;
            result.setQuery(query);
            return result;
        }
    }

    /** A query action override. Rule based override with a different query. */
    static class QueryActionOverride
    {
        @JsonProperty
        private Object rule;

        @JsonIgnore
        private IExpression ruleExpression;

        @JsonProperty
        private String queryName;

        @JsonIgnore
        private QueryActionQuery query;

        String getQueryName()
        {
            return queryName;
        }

        void setQueryName(String queryName)
        {
            this.queryName = queryName;
        }

        QueryActionQuery getQuery()
        {
            return query;
        }

        void setQuery(QueryActionQuery query)
        {
            this.query = query;
        }

        String getRule()
        {
            if (rule == null)
            {
                return "";
            }
            if (rule instanceof String)
            {
                return (String) rule;
            }
            else if (rule instanceof Collection)
            {
                return ((Collection<?>) rule).stream()
                        .map(Object::toString)
                        .collect(joining(System.lineSeparator()));
            }
            return String.valueOf(rule);
        }

        void setRule(Object rule)
        {
            if (!(rule instanceof Collection))
            {
                this.rule = new ArrayList<>(asList(String.valueOf(rule)
                        .split(System.lineSeparator())));
            }
            else
            {
                this.rule = rule;
            }
        }

        @Override
        public QueryActionOverride clone()
        {
            QueryActionOverride override = new QueryActionOverride();
            override.setRule(rule);
            override.setQuery(query);
            override.setQueryName(queryName);
            return override;
        }
    }

    /** A query action. Query that targets specific object types etc. */
    static class QueryAction
    {
        @JsonProperty
        private String title = "";

        @JsonProperty
        private String queryName;

        @JsonIgnore
        private QueryActionQuery query;

        @JsonProperty
        private List<QueryActionOverride> overrides = new ArrayList<>();

        @JsonProperty
        private Set<ActionTarget> actionTargets = new HashSet<>();

        @JsonProperty
        private Set<ActionType> actionTypes = new HashSet<>();

        @JsonProperty
        private Set<ObjectType> objectTypes = new HashSet<>();

        @JsonProperty
        private OutputType output = OutputType.TABLE;

        /** Sub items. If set then this item acts as parent container only with no action */
        @JsonProperty
        private List<QueryAction> subItems = new ArrayList<>();

        String getTitle()
        {
            return title;
        }

        void setTitle(String title)
        {
            this.title = title;
        }

        Set<ActionTarget> getActionTargets()
        {
            return actionTargets;
        }

        void setActionTargets(Set<ActionTarget> actionTargets)
        {
            this.actionTargets = actionTargets;
        }

        Set<ActionType> getActionTypes()
        {
            return actionTypes;
        }

        void setActionTypes(Set<ActionType> actionTypes)
        {
            this.actionTypes = actionTypes;
        }

        Set<ObjectType> getObjectTypes()
        {
            return objectTypes;
        }

        void setObjectTypes(Set<ObjectType> objectTypes)
        {
            this.objectTypes = objectTypes;
        }

        OutputType getOutput()
        {
            return output;
        }

        void setOutputType(OutputType output)
        {
            this.output = output;
        }

        QueryActionQuery getQuery()
        {
            return query;
        }

        void setQuery(QueryActionQuery query)
        {
            this.query = query;
        }

        String getQueryName()
        {
            return queryName;
        }

        void setQueryName(String queryName)
        {
            this.queryName = queryName;
        }

        List<QueryAction> getSubItems()
        {
            return subItems;
        }

        void setSubItems(List<QueryAction> subItems)
        {
            this.subItems = subItems;
        }

        List<QueryActionOverride> getOverrides()
        {
            return overrides;
        }

        void setOverrides(List<QueryActionOverride> overrides)
        {
            this.overrides = overrides;
        }

        @Override
        public QueryAction clone()
        {
            QueryAction action = new QueryAction();
            action.title = title;
            action.output = output;
            action.queryName = queryName;
            action.actionTargets = new HashSet<>(actionTargets);
            action.actionTypes = new HashSet<>(actionTypes);
            action.objectTypes = new HashSet<>(objectTypes);
            action.subItems = subItems.stream()
                    .map(QueryAction::clone)
                    .collect(toCollection(() -> new ArrayList<>()));
            action.overrides = overrides.stream()
                    .map(QueryActionOverride::clone)
                    .collect(toCollection(() -> new ArrayList<>()));
            return action;
        }
    }

    enum ActionTarget
    {
        /** Target of action is navigation tree */
        NAVIGATION_TREE,

        /** Target of action is objects in text editor */
        TEXT_EDITOR
    }

    enum ActionType
    {
        /** Action is shown when CTRL-hover on target {@link ObjectType} */
        LINK("Action is shown when CTRL/META-hover on target"),
        /** Action is shown when right click on target {@link ObjectType} in tree */
        CONTEXT_MENU("Action is shown when right click on target");

        private final String tooltip;

        ActionType(String tooltip)
        {
            this.tooltip = tooltip;
        }

        public String getTooltip()
        {
            return tooltip;
        }
    }
}
