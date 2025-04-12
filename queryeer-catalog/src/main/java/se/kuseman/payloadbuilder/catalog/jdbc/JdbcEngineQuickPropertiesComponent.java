package se.kuseman.payloadbuilder.catalog.jdbc;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.QueryFileMetaData;
import com.queryeer.api.component.AutoCompletionComboBox;
import com.queryeer.api.component.QueryeerTree;
import com.queryeer.api.component.QueryeerTree.FilterPath;
import com.queryeer.api.component.QueryeerTree.RegularNode;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.editor.TextSelection;
import com.queryeer.api.event.NewQueryFileEvent;
import com.queryeer.api.event.ShowOptionsEvent;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnectionsTreeModel.ConnectionNode;
import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnectionsTreeModel.DatabaseNode;
import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnectionsTreeModel.DatabasesNode;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DatabaseProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

/** Quick properties for JdbcEngine. */
class JdbcEngineQuickPropertiesComponent extends JPanel
{
    private final JdbcQueryEngine queryEngine;
    private final IQueryFileProvider queryFileProvider;
    private final JdbcConnectionsModel connectionsModel;
    private final IEventBus eventBus;
    private final CatalogCrawlService crawlService;
    private final JdbcConnectionsTreeConfigurable connectionsTreeConfigurable;
    private final JTextField currentConnection;
    private final JComboBox<String> databases;
    private final DefaultComboBoxModel<String> databasesModel;
    private final QueryeerTree tree;
    private final JdbcConnectionsTreeModel connectionsTreeModel;
    private final QueryeerTree.QueryeerTreeModel treeModel;

    private boolean suppressEvents = false;

    //@formatter:off
    JdbcEngineQuickPropertiesComponent(
            JdbcQueryEngine queryEngine,
            Icons icons,
            IQueryFileProvider queryFileProvider,
            JdbcConnectionsModel connectionsModel,
            IEventBus eventBus,
            DatabaseProvider databaseProvider,
            CatalogCrawlService crawlService,
            JdbcConnectionsTreeConfigurable connectionsTreeConfigurable)
    {
        //@formatter:on
        this.queryEngine = queryEngine;
        this.queryFileProvider = queryFileProvider;
        this.connectionsModel = connectionsModel;
        this.eventBus = eventBus;
        this.crawlService = crawlService;
        this.connectionsTreeConfigurable = connectionsTreeConfigurable;

        setLayout(new GridBagLayout());

        databases = new JComboBox<>();
        databases.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setIcon(icons.database);
                return label;
            }
        });
        databases.setMaximumRowCount(40);
        databases.addItemListener(l ->
        {
            if (suppressEvents)
            {
                return;
            }
            IQueryFile currentFile = queryFileProvider.getCurrentFile();
            if (l.getItem() instanceof String
                    && currentFile != null)
            {
                JdbcEngineState engineState = currentFile.getEngineState();
                ConnectionState state = engineState.connectionState;
                if (state != null)
                {
                    SwingUtilities.invokeLater(() ->
                    {
                        try
                        {
                            state.setDatabaseOnConnection((String) l.getItem());
                            if (connectionsTreeConfigurable.isFilterTree())
                            {
                                filterTree(state);
                            }
                        }
                        catch (SQLException e)
                        {
                            currentFile.getOutputComponent(ITextOutputComponent.class)
                                    .appendWarning(e.getMessage(), TextSelection.EMPTY);
                        }
                        setStatus(currentFile, state);
                    });
                }

                engineState.resetParser();

                // Re-parse content when we switch database
                if (currentFile.getEditor() instanceof ITextEditor textEditor)
                {
                    textEditor.parse();
                }
            }
        });

        databases.setEditable(false);
        databasesModel = new DefaultComboBoxModel<>();
        databases.setModel(databasesModel);
        AutoCompletionComboBox.enable(databases);

        currentConnection = new JTextField("");
        currentConnection.setEditable(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 2, 2);
        JPanel currentConnectionPanel = new JPanel(new GridBagLayout());
        currentConnectionPanel.add(new JLabel("Server:")
        {
            {
                setFont(getFont().deriveFont(Font.BOLD));
            }
        }, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0d;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        currentConnectionPanel.add(currentConnection, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0d;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        add(currentConnectionPanel, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0d;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JPanel databasesPanel = new JPanel(new GridBagLayout());
        databasesPanel.add(databases, gbc);

        JButton changeConnection = new JButton();
        changeConnection.setMaximumSize(new Dimension(18, 18));
        changeConnection.setMinimumSize(new Dimension(18, 18));
        changeConnection.setPreferredSize(new Dimension(18, 18));
        changeConnection.setIcon(icons.plug);
        changeConnection.setToolTipText("Change Connection");

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        databasesPanel.add(changeConnection, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0d;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        add(databasesPanel, gbc);

        changeConnection.addActionListener(l ->
        {
            IQueryFile currentFile = queryFileProvider.getCurrentFile();
            if (currentFile == null)
            {
                return;
            }

            if (connectionsModel.getSize() == 0)
            {
                eventBus.publish(new ShowOptionsEvent(JdbcConnectionsConfigurable.class));
                return;
            }

            Object[] values = connectionsModel.getConnections()
                    .toArray();

            Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                    .getActiveWindow();
            JdbcConnection result = (JdbcConnection) JOptionPane.showInputDialog(activeWindow, "Connection", "Change Connection", JOptionPane.QUESTION_MESSAGE, null, values, values[0]);

            if (result != null
                    && connectionsModel.prepare(result, false))
            {

                JdbcDatabase jdbcDatabase = databaseProvider.getDatabase(result.getJdbcURL());
                JdbcEngineState engineState = currentFile.getEngineState();
                ConnectionState state = engineState.connectionState;

                // Close old state if any
                if (state != null)
                {
                    JdbcQueryEngine.EXECUTOR.execute(() -> IOUtils.closeQuietly(state));
                }

                // Create new state
                ConnectionState newState = new ConnectionState(result, jdbcDatabase);
                engineState.setConnectionState(newState);
                queryEngine.focus(currentFile);
                // Lazy load databases
                loadConnectionMetaData(engineState);
            }
        });

        connectionsTreeModel = new JdbcConnectionsTreeModel(connectionsModel, icons, databaseProvider, node -> newQuery(node));
        treeModel = new QueryeerTree.QueryeerTreeModel(connectionsTreeModel);
        connectionsModel.addListDataListener(new ListDataListener()
        {
            @Override
            public void intervalRemoved(ListDataEvent e)
            {
            }

            @Override
            public void intervalAdded(ListDataEvent e)
            {
            }

            @Override
            public void contentsChanged(ListDataEvent e)
            {
                // Reload root when connections model changes
                treeModel.resetRoot();
            }
        });

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0d;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

        JButton collapseAll = new JButton(icons.getIconFactory()
                .getIcon(IIconFactory.Provider.FONTAWESOME, "WINDOW_MINIMIZE", 8));
        collapseAll.setToolTipText("Collapse All");
        buttonPanel.add(collapseAll);

        JToggleButton syncButton = new JToggleButton(icons.getIconFactory()
                .getIcon(IIconFactory.Provider.FONTAWESOME, "ARROWS_H", 8));
        syncButton.setToolTipText("Sync Tree With Selected Tab");
        syncButton.setSelected(connectionsTreeConfigurable.isSyncTree());
        syncButton.addActionListener(l ->
        {
            connectionsTreeConfigurable.setSyncTree(syncButton.isSelected());
            connectionsTreeConfigurable.saveConfig();
        });
        buttonPanel.add(syncButton);

        JToggleButton filterTreeButton = new JToggleButton(icons.getIconFactory()
                .getIcon(IIconFactory.Provider.FONTAWESOME, "FILTER", 8));
        filterTreeButton.setToolTipText("Filter Tree To Only Show The Tabs Connection");
        filterTreeButton.setSelected(connectionsTreeConfigurable.isFilterTree());
        filterTreeButton.addActionListener(l ->
        {
            ConnectionState state = null;
            IQueryFile currentFile = queryFileProvider.getCurrentFile();
            if (currentFile != null)
            {
                JdbcEngineState engineState = currentFile.getEngineState();
                state = engineState.connectionState;
            }

            // Always filter the tree when toggle the button
            filterTree(filterTreeButton.isSelected() ? state
                    : null);
            connectionsTreeConfigurable.setFilterTree(filterTreeButton.isSelected());
            connectionsTreeConfigurable.saveConfig();
        });
        buttonPanel.add(filterTreeButton);

        JButton config = new JButton(icons.getIconFactory()
                .getIcon(IIconFactory.Provider.FONTAWESOME, "COG", 8));
        config.setToolTipText("Show Connections Config");
        config.addActionListener(l -> eventBus.publish(new ShowOptionsEvent(JdbcConnectionsConfigurable.class)));
        buttonPanel.add(config);

        JButton treeConfig = new JButton(icons.getIconFactory()
                .getIcon(IIconFactory.Provider.FONTAWESOME, "COGS", 8));
        treeConfig.setToolTipText("Show Tree Config");
        treeConfig.addActionListener(l -> eventBus.publish(new ShowOptionsEvent(JdbcConnectionsTreeConfigurable.class)));
        buttonPanel.add(treeConfig);

        add(buttonPanel, gbc);

        tree = new QueryeerTree(treeModel);
        tree.getSelectionModel()
                .setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setHyperLinksEnabled(true);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0d;
        gbc.weighty = 1.0d;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        add(new JScrollPane(tree), gbc);
        setPreferredSize(new Dimension(240, 75));

        collapseAll.addActionListener(l ->
        {
            for (int i = tree.getRowCount() - 1; i >= 0; i--)
            {
                tree.collapseRow(i);
            }
        });
    }

    private void newQuery(RegularNode node)
    {
        ConnectionState state = null;
        if (node instanceof ConnectionNode cnode)
        {
            state = cnode.createState();
        }
        else if (node instanceof DatabaseNode dnode)
        {
            state = dnode.createState();
        }

        if (state != null
                && connectionsModel.prepare(state.getJdbcConnection(), false))
        {
            JdbcEngineState engineState = new JdbcEngineState(queryEngine, state);
            eventBus.publish(new NewQueryFileEvent(queryEngine, engineState, null, false, null));
            // Lazy load databases
            loadConnectionMetaData(engineState);
        }
    }

    private void loadConnectionMetaData(JdbcEngineState state)
    {
        if (state.getJdbcConnection()
                .getDatabases() == null)
        {
            JdbcQueryEngine.EXECUTOR.execute(() ->
            {
                connectionsModel.getDatabases(state.getJdbcConnection(), true, false);

                IQueryFile currentFile = queryFileProvider.getCurrentFile();
                if (currentFile != null)
                {
                    JdbcEngineState currentEngineState = currentFile.getEngineState();
                    ConnectionState currentState = currentEngineState.connectionState;
                    // If the current file is the same as the one we loaded databases on then re-focus it
                    if (currentState != null
                            && currentState.getJdbcConnection() == state.getJdbcConnection())
                    {
                        SwingUtilities.invokeLater(() -> focus(currentFile));
                    }
                }
            });
        }
        if (state.getDatabase() != null)
        {
            crawlService.getCatalog(state, state.getDatabase());
        }
    }

    void setSelectedDatabase(ConnectionState state)
    {
        suppressEvents = true;
        try
        {
            databasesModel.setSelectedItem(state.getDatabase());
        }
        finally
        {
            suppressEvents = false;
        }
    }

    void setStatus(IQueryFile file, ConnectionState state)
    {
        String connectionInfo = "<html><font color=\"%s\"><b>%s: %s / %s%s / %s</b></font>".formatted(Objects.toString(state.getJdbcConnection()
                .getColor(), "#000000"), state.getJdbcDatabase()
                        .getSessionKeyword(),
                isBlank(state.getSessionId()) ? "Not connected"
                        : state.getSessionId(),
                state.getJdbcConnection()
                        .getName(),
                (!isBlank(state.getDatabase()) ? " / " + state.getDatabase()
                        : ""),
                state.getJdbcConnection()
                        .getUsername());

        file.setMetaData(new QueryFileMetaData(connectionInfo, state.getSessionId()));
    }

    void focus(IQueryFile queryFile)
    {
        suppressEvents = true;

        try
        {
            databasesModel.removeAllElements();
            databases.setEnabled(false);
            JdbcEngineState engineState = queryFile.getEngineState();
            ConnectionState state = engineState.connectionState;
            currentConnection.setText(state != null ? state.getJdbcConnection()
                    .getName()
                    : "");
            if (state != null)
            {
                // Force a reparse when we focus a new file
                if (queryFile.getEditor() instanceof ITextEditor textEditor)
                {
                    if (textEditor.getEditorKit() instanceof JdbcTextEditorKit kit)
                    {
                        kit.updateActionStatuses();
                    }
                    textEditor.parse();
                }

                if (state.getJdbcConnection()
                        .getDatabases() != null)
                {
                    databasesModel.addAll(state.getJdbcConnection()
                            .getDatabases());
                    databasesModel.setSelectedItem(state.getDatabase());
                    databases.setEnabled(true);
                }
                if (!connectionsTreeConfigurable.isFilterTree()
                        && connectionsTreeConfigurable.isSyncTree())
                {
                    selectTreeNode(state);
                }
                setStatus(queryFile, state);
            }
            else
            {
                queryFile.setMetaData(new QueryFileMetaData("<html><font color=\"#000000\"><b>Not connected</b></font></html>", ""));
            }

            if (connectionsTreeConfigurable.isFilterTree())
            {
                filterTree(state);
            }
        }
        finally
        {
            suppressEvents = false;
        }
    }

    private void filterTree(ConnectionState state)
    {
        if (state == null)
        {
            treeModel.setFilterPaths(null);
        }
        else
        {
            ConnectionNode connectionsNode = connectionsTreeModel.new ConnectionNode(state.getJdbcConnection());
            // Filter connection only
            if (isBlank(state.getDatabase()))
            {
                treeModel.setFilterPaths(List.of(new FilterPath(new RegularNode[] { connectionsNode }, true)));
            }
            // .. or filter connection and database
            else
            {
                DatabasesNode databasesNode = connectionsTreeModel.new DatabasesNode(connectionsNode);
                DatabaseNode databaseNode = connectionsTreeModel.new DatabaseNode(connectionsNode, state.getDatabase());

                treeModel.setFilterPaths(List.of(new FilterPath(new RegularNode[] { connectionsNode, databasesNode, databaseNode }, true)));
            }
        }
    }

    private void selectTreeNode(ConnectionState state)
    {
        AtomicBoolean done = new AtomicBoolean(false);
        AtomicReference<TreePath> selectPath = new AtomicReference<>();
        while (!done.get())
        {
            done.set(true);
            tree.treeEnumeration((s, n) ->
            {
                if (n instanceof JdbcConnectionsTreeModel.DatabaseNode dn
                        && dn.getJdbcConnection() == state.getJdbcConnection()
                        && StringUtils.equals(state.getDatabase(), dn.getDatabase()))
                {
                    selectPath.set(s.get());
                    return false;
                }
                else if (n instanceof JdbcConnectionsTreeModel.DatabasesNode dn
                        && dn.getJdbcConnection() == state.getJdbcConnection())
                {
                    boolean hasDatabases = !CollectionUtils.isEmpty(dn.getJdbcConnection()
                            .getDatabases());
                    if (hasDatabases)
                    {
                        // Expand databases node and restart enumeration
                        TreePath treePath = s.get();
                        if (tree.isCollapsed(treePath))
                        {
                            tree.expandPath(treePath);
                            done.set(false);
                            return false;
                        }
                    }
                }
                else if (n instanceof JdbcConnectionsTreeModel.ConnectionNode cn
                        && cn.getJdbcConnection() == state.getJdbcConnection())
                {
                    TreePath treePath = s.get();

                    // Check to see if the can expand the connection if database are loaded
                    // If so expand the node and restart the enumeration
                    if (tree.isCollapsed(treePath)
                            && cn.shouldLoadChildren())
                    {
                        tree.expandPath(treePath);
                        selectPath.set(null);
                        done.set(false);
                        return false;
                    }

                    selectPath.set(treePath);
                }

                return true;
            });
        }

        if (selectPath.get() != null)
        {
            tree.scrollPathToVisible(selectPath.get());
            tree.selectNode(selectPath.get());
        }
    }
}
