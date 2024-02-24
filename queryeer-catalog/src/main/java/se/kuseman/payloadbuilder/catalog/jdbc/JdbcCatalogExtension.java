package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataListener;

import org.apache.commons.lang3.mutable.MutableObject;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.AutoCompletionComboBox;
import com.queryeer.api.event.QueryFileChangedEvent;
import com.queryeer.api.event.QueryFileStateEvent;
import com.queryeer.api.event.QueryFileStateEvent.State;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICompletionProvider;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IIconFactory.Provider;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.utils.CredentialUtils.Credentials;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.Common;
import se.kuseman.payloadbuilder.catalog.CredentialsException;

/** Queryeer extension for {@link JdbcCatalog}. */
class JdbcCatalogExtension implements ICatalogExtension
{
    static final String TITLE = "Jdbc";
    static final JdbcCatalog CATALOG = new JdbcCatalog();
    private final JdbcConnectionsModel connectionsModel;
    private final JdbcCompletionProvider completionProvider;
    private final IQueryFileProvider queryFileProvider;
    private final String catalogAlias;
    private final PropertiesComponent propertiesComponent;
    private final IIconFactory iconFactory;

    JdbcCatalogExtension(JdbcConnectionsModel connectionsModel, IQueryFileProvider queryFileProvider, String catalogAlias, IIconFactory iconFactory)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.completionProvider = new JdbcCompletionProvider(connectionsModel);
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.catalogAlias = catalogAlias;
        this.iconFactory = requireNonNull(iconFactory, "iconFactory");
        this.propertiesComponent = new PropertiesComponent();
    }

    @Override
    public String getTitle()
    {
        return TITLE;
    }

    @Override
    public Catalog getCatalog()
    {
        return CATALOG;
    }

    @Override
    public Class<? extends IConfigurable> getConfigurableClass()
    {
        return JdbcCatalogConfigurable.class;
    }

    @Override
    public boolean hasQuickPropertieComponent()
    {
        return true;
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
            JdbcConnectionsModel.Connection selectedConnection = (JdbcConnectionsModel.Connection) propertiesComponent.connections.getSelectedItem();

            // Try to find the connection from session
            JdbcConnectionsModel.Connection connection = connectionsModel.findConnection(querySession, catalogAlias);

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
                Credentials credentials = connectionsModel.getCredentials(url, username, false);
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
        return propertiesComponent;
    }

    @Override
    public ICompletionProvider getAutoCompletionProvider()
    {
        return completionProvider;
    }

    @Subscribe
    private void queryFileChanged(QueryFileChangedEvent event)
    {
        update();
    }

    @Subscribe
    private void queryFileStateChanged(QueryFileStateEvent event)
    {
        IQueryFile queryFile = event.getQueryFile();
        if (queryFileProvider.getCurrentFile() == queryFile)
        {
            // Update quick properties with changed properties from query session
            if (event.getState() == State.AFTER_QUERY_EXECUTE)
            {
                update();
            }
        }
    }

    private void setupConnection(JdbcConnectionsModel.Connection connection)
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }
        IQuerySession session = queryFile.getSession();
        if (connection != null)
        {
            connection.setup(session, catalogAlias);
            session.setCatalogProperty(catalogAlias, JdbcCatalog.DATABASE, (String) null);
        }
    }

    private void setupDatabase(JdbcConnectionsModel.Connection connection, String database)
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }
        IQuerySession session = queryFile.getSession();
        if (connection != null
                && !isBlank(database))
        {
            session.setCatalogProperty(catalogAlias, JdbcCatalog.DATABASE, database);
        }
    }

    private void update()
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }
        IQuerySession session = queryFile.getSession();
        String url = session.getCatalogProperty(catalogAlias, JdbcCatalog.URL)
                .valueAsString(0);
        String database = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);
        String schema = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);

        MutableObject<JdbcConnectionsModel.Connection> connectionToSelect = new MutableObject<>();
        MutableObject<String> databaseToSelect = new MutableObject<>();

        int size = connectionsModel.getSize();
        for (int i = 0; i < size; i++)
        {
            JdbcConnectionsModel.Connection connection = connectionsModel.getElementAt(i);

            if (!equalsIgnoreCase(url, connection.getJdbcURL()))
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
            propertiesComponent.connections.setSelectedItem(connectionToSelect.getValue());
            propertiesComponent.databases.setSelectedItem(databaseToSelect.getValue());
        });
    }

    /** Properties component. */
    private class PropertiesComponent extends JPanel
    {
        private final Icon lock = iconFactory.getIcon(Provider.FONTAWESOME, "LOCK");
        private final Icon unlock = iconFactory.getIcon(Provider.FONTAWESOME, "UNLOCK");

        private static final String PROTOTYPE_CATALOG = "somedatabasewithalongname";
        private final JComboBox<JdbcConnectionsModel.Connection> connections;
        private final JComboBox<String> databases;
        private final DefaultComboBoxModel<String> databasesModel = new DefaultComboBoxModel<>();
        private final JButton reload;
        private final JLabel authStatus;

        PropertiesComponent()
        {
            setLayout(new GridBagLayout());

            authStatus = new JLabel();
            connections = new JComboBox<>(new ConnectionsSelectionModel());
            databases = new JComboBox<>(databasesModel);
            reload = new JButton("Reload");
            connectionsModel.registerReloadButton(reload);
            reload.addActionListener(l ->
            {
                JdbcConnectionsModel.Connection connection = (JdbcConnectionsModel.Connection) connections.getSelectedItem();
                if (connection == null)
                {
                    return;
                }

                // Preparation aborted, don't load indices
                if (!connectionsModel.prepare(connection, false))
                {
                    return;
                }

                reload(connection, false);
                setupConnection(connection);
            });

            connections.addItemListener(l ->
            {
                JdbcConnectionsModel.Connection connection = (JdbcConnectionsModel.Connection) connections.getSelectedItem();
                if (connection != null)
                {
                    // Prepare connection silent
                    connectionsModel.prepare(connection, true);
                    setupConnection(connection);
                    reload(connection, true);
                    populateDatabases(connection);
                }
                updateAuthStatus(connection);
            });

            AutoCompletionComboBox.enable(databases);
            databases.setPrototypeDisplayValue(PROTOTYPE_CATALOG);
            databases.addItemListener(l ->
            {
                setupDatabase((JdbcConnectionsModel.Connection) connections.getSelectedItem(), (String) databases.getSelectedItem());
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

        private void updateAuthStatus(JdbcConnectionsModel.Connection connection)
        {
            // Only update if the provided connection is the selected one
            if (connection == connections.getSelectedItem())
            {
                boolean hasCredentials = connection != null
                        && connection.hasCredentials();

                authStatus.setIcon(hasCredentials ? unlock
                        : lock);
                authStatus.setToolTipText(hasCredentials ? null
                        : Common.AUTH_STATUS_LOCKED_TOOLTIP);
            }
        }

        private void reload(JdbcConnectionsModel.Connection connection, boolean suppressError)
        {
            if (!connection.hasCredentials())
            {
                return;
            }

            connectionsModel.setEnableRealod(false);
            Thread t = new Thread(() ->
            {
                try (Connection sqlConnection = CATALOG.getConnection(connection.getJdbcDriverClassName(), connection.getJdbcURL(), connection.getUsername(),
                        new String(connection.getRuntimePassword()), catalogAlias))
                {
                    List<String> databases = new ArrayList<>();
                    try (ResultSet rs = sqlConnection.getMetaData()
                            .getCatalogs())
                    {
                        while (rs.next())
                        {
                            databases.add(rs.getString(1));
                        }
                    }

                    // Try schemas
                    if (databases.isEmpty())
                    {
                        try (ResultSet rs = sqlConnection.getMetaData()
                                .getSchemas())
                        {
                            while (rs.next())
                            {
                                databases.add(rs.getString(1));
                            }
                        }

                        if (!databases.isEmpty())
                        {
                            connection.setUsesSchemas(true);
                        }
                    }

                    databases.sort((a, b) -> a.compareTo(b));
                    connection.setDatabases(databases);
                    SwingUtilities.invokeLater(() ->
                    {
                        populateDatabases(connection);
                        updateAuthStatus(connection);
                    });
                }
                catch (Exception e)
                {
                    if (!suppressError)
                    {
                        JOptionPane.showMessageDialog(null, e.getMessage(), "Error reloading databases", JOptionPane.ERROR_MESSAGE);
                    }
                    else
                    {
                        IQueryFile queryFile = queryFileProvider.getCurrentFile();
                        if (queryFile != null)
                        {
                            e.printStackTrace(queryFile.getMessagesWriter());
                            queryFile.focusMessages();
                        }
                        else
                        {
                            e.printStackTrace();
                        }
                    }
                }
                finally
                {
                    connectionsModel.setEnableRealod(true);
                }
            });
            t.start();
        }

        private void populateDatabases(JdbcConnectionsModel.Connection connection)
        {
            databasesModel.removeAllElements();
            for (String database : connection.getDatabases())
            {
                databasesModel.addElement(database);
            }
        }
    }

    private class ConnectionsSelectionModel extends DefaultComboBoxModel<JdbcConnectionsModel.Connection>
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
        public JdbcConnectionsModel.Connection getElementAt(int index)
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
