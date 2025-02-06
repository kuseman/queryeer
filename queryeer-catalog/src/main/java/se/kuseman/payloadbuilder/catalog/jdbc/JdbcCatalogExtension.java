package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataListener;

import org.apache.commons.lang3.mutable.MutableObject;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.AutoCompletionComboBox;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.engine.IQueryEngine.IState.MetaParameter;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtensionView;
import com.queryeer.api.extensions.payloadbuilder.ICompletionProvider;
import com.queryeer.api.extensions.payloadbuilder.IPayloadbuilderState;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.utils.CredentialUtils.Credentials;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.Common;
import se.kuseman.payloadbuilder.catalog.CredentialsException;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DatabaseProvider;

/** Queryeer extension for {@link JdbcCatalog}. */
class JdbcCatalogExtension implements ICatalogExtension
{
    static final JdbcCatalog CATALOG = new JdbcCatalog();
    private final JdbcConnectionsModel connectionsModel;
    private final JdbcCompletionProvider completionProvider;
    private final IQueryFileProvider queryFileProvider;
    private final String catalogAlias;
    private final Icons icons;
    private PropertiesComponent propertiesComponent;

    JdbcCatalogExtension(JdbcConnectionsModel connectionsModel, IQueryFileProvider queryFileProvider, CatalogCrawlService crawlService, Icons icons, DatabaseProvider databaseProvider,
            String catalogAlias)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.completionProvider = new JdbcCompletionProvider(connectionsModel, requireNonNull(crawlService, "crawlService"), requireNonNull(databaseProvider, "databaseProvider"));
        this.icons = requireNonNull(icons, "icons");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.catalogAlias = catalogAlias;
    }

    @Override
    public String getTitle()
    {
        return Constants.TITLE;
    }

    @Override
    public Catalog getCatalog()
    {
        return CATALOG;
    }

    @Override
    public Class<? extends IConfigurable> getConfigurableClass()
    {
        return JdbcConnectionsConfigurable.class;
    }

    @Override
    public boolean hasQuickPropertieComponent()
    {
        return true;
    }

    @Override
    public List<MetaParameter> getMetaParameters(IQuerySession querySession, boolean testData)
    {
        String url = "jdbc://server/database";
        String database = "database";
        if (!testData)
        {
            url = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.URL)
                    .valueAsString(0);
            database = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                    .valueAsString(0);
        }

        return IConnectionState.getMetaParameters(url, database);
    }

    @Override
    public ExceptionAction handleException(IQuerySession querySession, CatalogException exception)
    {
        String catalogAlias = exception.getCatalogAlias();
        boolean askForCredentials = false;
        // Credentials exception thrown, ask for credentials
        if (exception instanceof CredentialsException
                || exception instanceof ConnectionException)
        {
            askForCredentials = true;
        }

        if (askForCredentials)
        {
            JdbcConnection selectedConnection = (JdbcConnection) propertiesComponent.connections.getSelectedItem();

            // Try to find the connection from session
            JdbcConnection connection = connectionsModel.findConnection(querySession, catalogAlias);

            // We have another connection in session than the one selected, populate session and re-run
            if (connection != null
                    && selectedConnection != null
                    && selectedConnection != connection)
            {
                if (!connectionsModel.prepare(connection, false))
                {
                    return ExceptionAction.NONE;
                }

                connection.setup(querySession, catalogAlias);
                return ExceptionAction.RERUN;
            }

            // Session contains data that don't exists in our connections model, ask for user pass
            // and populate session
            if (connection == null)
            {
                String url = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.URL)
                        .valueAsString(0);
                String username = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.USERNAME)
                        .valueAsString(0);
                Credentials credentials = connectionsModel.getCredentials(url, username, false, null);
                if (credentials == null)
                {
                    return ExceptionAction.NONE;
                }

                querySession.setCatalogProperty(catalogAlias, JdbcCatalog.USERNAME, credentials.getUsername());
                querySession.setCatalogProperty(catalogAlias, JdbcCatalog.PASSWORD, credentials.getPassword());
                return ExceptionAction.RERUN;
            }

            // Clear the runtime password since we have a credentials exception
            connection.setRuntimePassword(null);
            // Prepare connection and rerun query
            if (!connectionsModel.prepare(connection, false))
            {
                return ExceptionAction.NONE;
            }

            connection.setup(querySession, catalogAlias);
            propertiesComponent.updateAuthStatus(connection);
            return ExceptionAction.RERUN;
        }

        return ExceptionAction.NONE;
    }

    @Override
    public Component getQuickPropertiesComponent()
    {
        if (propertiesComponent == null)
        {
            this.propertiesComponent = new PropertiesComponent(icons, connectionsModel, (con) -> setupConnection(con), (con, database) -> setupDatabase(con, database), queryFile ->
            {
                // Update properties if was the current file that completed execution
                if (queryFileProvider.getCurrentFile() == queryFile)
                {
                    update(queryFile);
                }
            }, queryFile -> update(queryFile));
        }
        return propertiesComponent;
    }

    @Override
    public ICompletionProvider getAutoCompletionProvider()
    {
        return completionProvider;
    }

    private void setupConnection(JdbcConnection connection)
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }

        if (connection != null)
        {
            if (queryFile.getEngineState() instanceof IPayloadbuilderState state)
            {
                connection.setup(state.getQuerySession(), catalogAlias);
                state.getQuerySession()
                        .setCatalogProperty(catalogAlias, JdbcCatalog.DATABASE, (String) null);
            }
        }
    }

    private void setupDatabase(JdbcConnection connection, String database)
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }
        if (queryFile.getEngineState() instanceof IPayloadbuilderState state)
        {
            if (connection != null
                    && !isBlank(database))
            {
                state.getQuerySession()
                        .setCatalogProperty(catalogAlias, JdbcCatalog.DATABASE, database);
            }
        }
    }

    private void update(IQueryFile queryFile)
    {
        IPayloadbuilderState state = (IPayloadbuilderState) queryFile.getEngineState();
        if (state == null)
        {
            return;
        }

        IQuerySession session = state.getQuerySession();
        String url = session.getCatalogProperty(catalogAlias, JdbcCatalog.URL)
                .valueAsString(0);
        String database = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);
        String schema = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);

        MutableObject<JdbcConnection> connectionToSelect = new MutableObject<>();
        MutableObject<String> databaseToSelect = new MutableObject<>();

        int size = connectionsModel.getSize();
        for (int i = 0; i < size; i++)
        {
            JdbcConnection connection = connectionsModel.getElementAt(i);

            if (!equalsIgnoreCase(url, connection.getJdbcURL()))
            {
                continue;
            }
            else if (connection.getDatabases() == null)
            {
                continue;
            }

            connectionToSelect.setValue(connection);

            int size2 = connection.getDatabases()
                    .size();
            for (int j = 0; j < size2; j++)
            {
                String connectionDatabase = connection.getDatabases()
                        .get(j);

                if (equalsIgnoreCase(database, connectionDatabase)
                        || equalsIgnoreCase(schema, connectionDatabase))
                {
                    databaseToSelect.setValue(connectionDatabase);
                    break;
                }
            }
            break;
        }

        if (connectionToSelect.getValue() == null
                && connectionsModel.getSize() > 0)
        {
            connectionToSelect.setValue(connectionsModel.getElementAt(0));
        }

        SwingUtilities.invokeLater(() ->
        {
            propertiesComponent.suppressEvents = true;
            try
            {
                propertiesComponent.connections.setSelectedItem(connectionToSelect.getValue());
                propertiesComponent.populateDatabases(connectionToSelect.getValue());
                propertiesComponent.databases.setSelectedItem(databaseToSelect.getValue());
            }
            finally
            {
                propertiesComponent.suppressEvents = false;
            }
        });
    }

    /** Properties component. */
    static class PropertiesComponent extends JPanel implements ICatalogExtensionView
    {
        private final JdbcConnectionsModel connectionsModel;
        private final Consumer<IQueryFile> afterExecuteConsumer;
        private final Consumer<IQueryFile> focusConsumer;

        private static final String PROTOTYPE_CATALOG = "somedatabasewithalongname";
        private final JComboBox<JdbcConnection> connections;
        private final JComboBox<String> databases;
        private final DefaultComboBoxModel<String> databasesModel = new DefaultComboBoxModel<>();
        private final JButton reload;
        private final JLabel authStatus;
        private final Icons icons;
        private boolean suppressEvents = false;

        PropertiesComponent(Icons icons, JdbcConnectionsModel connectionsModel, Consumer<JdbcConnection> connectionConsumer, BiConsumer<JdbcConnection, String> databaseConsumer,
                Consumer<IQueryFile> afterExecuteConsumer, Consumer<IQueryFile> focusConsumer)
        {
            this.afterExecuteConsumer = afterExecuteConsumer;
            this.focusConsumer = focusConsumer;
            this.icons = requireNonNull(icons, "icons");
            this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
            requireNonNull(connectionConsumer, "connectionConsumer");
            requireNonNull(databaseConsumer, "databaseConsumer");

            setLayout(new GridBagLayout());

            authStatus = new JLabel();
            connections = new JComboBox<>(new ConnectionsSelectionModel());
            connections.setRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof JdbcConnection connection)
                    {
                        if (!connection.isEnabled())
                        {
                            setText("<html><i>" + connection.toString() + "</i></html>");
                        }
                        else
                        {
                            setText(connection.toString());
                        }
                    }

                    return this;
                }
            });
            databases = new JComboBox<>(databasesModel);
            reload = new JButton("Reload");
            connectionsModel.registerReloadButton(reload);
            reload.addActionListener(l ->
            {
                JdbcConnection connection = (JdbcConnection) connections.getSelectedItem();
                if (connection == null
                        || !connection.isEnabled())
                {
                    return;
                }

                // Preparation aborted, don't load databases
                if (!connectionsModel.prepare(connection, false))
                {
                    return;
                }

                reload(connection, true);
                connectionConsumer.accept(connection);
            });

            connections.addItemListener(l ->
            {
                if (suppressEvents)
                {
                    return;
                }

                suppressEvents = true;
                try
                {
                    JdbcConnection connection = (JdbcConnection) connections.getSelectedItem();
                    databasesModel.removeAllElements();
                    if (connection != null)
                    {
                        // Prepare connection silent
                        connectionsModel.prepare(connection, true);
                        connectionConsumer.accept(connection);
                        reload(connection, false);
                    }
                    updateAuthStatus(connection);
                }
                finally
                {
                    suppressEvents = false;
                }
            });

            AutoCompletionComboBox.enable(databases);
            databases.setPrototypeDisplayValue(PROTOTYPE_CATALOG);
            databases.addItemListener(l ->
            {
                if (suppressEvents)
                {
                    return;
                }

                databaseConsumer.accept((JdbcConnection) connections.getSelectedItem(), (String) databases.getSelectedItem());
            });
            // CSOFF
            databases.setMaximumRowCount(25);
            // CSON
            add(new JLabel("Server"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 1, 3), 0, 0));
            add(connections, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 1, 0), 0, 0));
            add(new JLabel("Database"), new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 1, 3), 0, 0));
            add(databases, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 1, 0), 0, 0));
            add(authStatus, new GridBagConstraints(0, 2, 1, 1, 0.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(3, 0, 0, 0), 0, 0));
            add(reload, new GridBagConstraints(1, 2, 1, 1, 1.0, 1.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
            // CSOFF
            setPreferredSize(new Dimension(240, 75));
            // CSON
        }

        @Override
        public void afterExecute(IQueryFile queryFile)
        {
            if (afterExecuteConsumer != null)
            {
                afterExecuteConsumer.accept(queryFile);
            }
        }

        @Override
        public void focus(IQueryFile queryFile)
        {
            if (focusConsumer != null)
            {
                focusConsumer.accept(queryFile);
            }
        }

        private void updateAuthStatus(JdbcConnection connection)
        {
            // Only update if the provided connection is the selected one
            if (connection == connections.getSelectedItem())
            {
                boolean hasCredentials = connection != null
                        && connection.hasCredentials();

                authStatus.setIcon(hasCredentials ? icons.unlock
                        : icons.lock);
                authStatus.setToolTipText(hasCredentials ? null
                        : Common.AUTH_STATUS_LOCKED_TOOLTIP);
            }
        }

        private void reload(JdbcConnection connection, boolean forceReload)
        {
            if (!connection.hasCredentials())
            {
                return;
            }

            Runnable load = () ->
            {
                connectionsModel.getDatabases(connection, forceReload, false);
                SwingUtilities.invokeLater(() ->
                {
                    populateDatabases(connection);
                    updateAuthStatus(connection);
                });
            };

            new Thread(load).start();
        }

        private void populateDatabases(JdbcConnection connection)
        {
            databasesModel.removeAllElements();
            if (connection == null
                    || connection.getDatabases() == null)
            {
                return;
            }
            for (String database : connection.getDatabases())
            {
                databasesModel.addElement(database);
            }
        }

        private class ConnectionsSelectionModel extends DefaultComboBoxModel<JdbcConnection>
        {
            private int selectedItemIndex;

            @Override
            public Object getSelectedItem()
            {
                if (selectedItemIndex >= 0
                        && selectedItemIndex < connectionsModel.getConnections()
                                .size())
                {
                    return connectionsModel.getElementAt(selectedItemIndex);
                }
                return null;
            }

            @Override
            public void setSelectedItem(Object connection)
            {
                int newIndex = connectionsModel.getConnections()
                        .indexOf(connection);
                if (newIndex == -1)
                {
                    return;
                }

                if (selectedItemIndex != newIndex)
                {
                    selectedItemIndex = newIndex;
                    fireContentsChanged(this, -1, -1);
                }
            }

            @Override
            public int getSize()
            {
                return connectionsModel.getSize();
            }

            @Override
            public JdbcConnection getElementAt(int index)
            {
                return connectionsModel.getElementAt(index);
            }

            @Override
            public void addListDataListener(ListDataListener l)
            {
                super.addListDataListener(l);
                connectionsModel.addListDataListener(l);
            }

            @Override
            public void removeListDataListener(ListDataListener l)
            {
                super.removeListDataListener(l);
                connectionsModel.removeListDataListener(l);
            }
        }
    }
}
