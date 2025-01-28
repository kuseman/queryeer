package se.kuseman.payloadbuilder.catalog.jdbc;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Objects;

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
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.tree.TreeSelectionModel;

import org.apache.commons.io.IOUtils;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.QueryFileMetaData;
import com.queryeer.api.component.AutoCompletionComboBox;
import com.queryeer.api.component.QueryeerTree;
import com.queryeer.api.component.QueryeerTree.RegularNode;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.event.NewQueryFileEvent;
import com.queryeer.api.event.ShowOptionsEvent;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnectionsTreeModel.ConnectionNode;
import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnectionsTreeModel.DatabaseNode;
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

    private final JTextField currentConnection;
    private final JComboBox<String> databases;
    private final DefaultComboBoxModel<String> databasesModel;
    private boolean suppressEvents = false;

    //@formatter:off
    JdbcEngineQuickPropertiesComponent(
            JdbcQueryEngine queryEngine,
            Icons icons,
            IQueryFileProvider queryFileProvider,
            JdbcConnectionsModel connectionsModel,
            IEventBus eventBus,
            DatabaseProvider databaseProvider,
            CatalogCrawlService crawlService)
    {
        //@formatter:on
        this.queryEngine = queryEngine;
        this.queryFileProvider = queryFileProvider;
        this.connectionsModel = connectionsModel;
        this.eventBus = eventBus;
        this.crawlService = crawlService;

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
                    // TODO: Do not run this on EDT
                    state.setDatabaseOnConnection((String) l.getItem(), currentFile);
                    setStatus(currentFile, state);
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

        JdbcConnectionsTreeModel connectionsTreeModel = new JdbcConnectionsTreeModel(connectionsModel, icons, databaseProvider, node -> newQuery(node));
        QueryeerTree.QueryeerTreeModel treeModel = new QueryeerTree.QueryeerTreeModel(connectionsTreeModel);
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

        QueryeerTree tree = new QueryeerTree(treeModel);
        tree.getSelectionModel()
                .setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setHyperLinksEnabled(true);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0d;
        gbc.weighty = 1.0d;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        add(new JScrollPane(tree), gbc);
        setPreferredSize(new Dimension(240, 75));
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
        databasesModel.setSelectedItem(state.getDatabase());
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
            if (currentConnection != null)
            {
                currentConnection.setText(state != null ? state.getJdbcConnection()
                        .getName()
                        : "");
            }
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

                setStatus(queryFile, state);
            }
            else
            {
                queryFile.setMetaData(new QueryFileMetaData("<html><font color=\"#000000\"><b>Not connected</b></font></html>", ""));
            }
        }
        finally
        {
            suppressEvents = false;
        }
    }
}
