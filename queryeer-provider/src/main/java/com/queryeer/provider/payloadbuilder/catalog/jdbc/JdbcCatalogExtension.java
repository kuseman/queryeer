package com.queryeer.provider.payloadbuilder.catalog.jdbc;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataListener;

import org.apache.commons.lang3.mutable.MutableObject;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.event.QueryFileChangedEvent;
import com.queryeer.api.event.QueryFileStateEvent;
import com.queryeer.api.event.QueryFileStateEvent.State;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.IPayloadbuilderQueryFileState;
import com.queryeer.api.service.IComponentFactory;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.utils.CredentialUtils;
import com.queryeer.api.utils.CredentialUtils.Credentials;
import com.queryeer.jdbc.IConnectionFactory;
import com.queryeer.jdbc.IJdbcConfigurable;
import com.queryeer.jdbc.IJdbcConnection;
import com.queryeer.jdbc.IJdbcConnectionListModel;
import com.queryeer.jdbc.Utils;
import com.queryeer.jdbc.Utils.CredentialsResult;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.session.IQuerySession;
import se.kuseman.payloadbuilder.catalog.CredentialsException;
import se.kuseman.payloadbuilder.catalog.jdbc.ConnectionException;
import se.kuseman.payloadbuilder.catalog.jdbc.JdbcCatalog;

/** Queryeer extension for {@link JdbcCatalog}. */
class JdbcCatalogExtension implements ICatalogExtension
{
    static final String TITLE = "Jdbc";
    private static final JdbcCatalog CATALOG = new JdbcCatalog();

    private final IJdbcConnectionListModel connectionsModel;
    private final IConnectionFactory connectionFactory;
    private final IQueryFileProvider queryFileProvider;
    private final IComponentFactory componentFactory;
    private final String catalogAlias;
    private final PropertiesComponent propertiesComponent;
    private final Map<IJdbcConnection, List<String>> databasesByConnection = new HashMap<>();

    JdbcCatalogExtension(IJdbcConnectionListModel connectionsModel, IConnectionFactory connectionFactory, IQueryFileProvider queryFileProvider, IComponentFactory componentFactory, String catalogAlias)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.connectionFactory = requireNonNull(connectionFactory, "connectionFactory");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.componentFactory = requireNonNull(componentFactory, "componentFactory");
        this.catalogAlias = catalogAlias;
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
        return IJdbcConfigurable.class;
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
            IJdbcConnection connection = (IJdbcConnection) propertiesComponent.connections.getSelectedItem();
            String url = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.URL);

            boolean isSelectedConnection = connection != null
                    && equalsIgnoreCase(url, connection.getJdbcURL());

            String connectionDescription = isSelectedConnection ? connection.toString()
                    : url;
            String prefilledUsername = isSelectedConnection ? connection.getUsername()
                    : querySession.getCatalogProperty(catalogAlias, JdbcCatalog.USERNAME);

            Credentials credentials = CredentialUtils.getCredentials(connectionDescription, prefilledUsername);
            if (credentials != null)
            {
                // CSOFF
                if (isSelectedConnection
                        && Objects.equals(credentials.getUsername(), connection.getUsername()))
                // CSON
                {
                    // connection.username = credentials.getUsername();
                    connection.setPassword(credentials.getPassword());

                    // Utilize connection to reload databases if not already done
                    // CSOFF
                    if (databasesByConnection.getOrDefault(connection, emptyList())
                            .size() == 0)
                    // CSON
                    {
                        propertiesComponent.reload(connection, true);
                    }
                }
                querySession.setCatalogProperty(catalogAlias, JdbcCatalog.USERNAME, credentials.getUsername());
                querySession.setCatalogProperty(catalogAlias, JdbcCatalog.PASSWORD, credentials.getPassword());
                return ExceptionAction.RERUN;
            }
        }

        return ExceptionAction.NONE;
    }

    @Override
    public Component getQuickPropertiesComponent()
    {
        return propertiesComponent;
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
            else if (event.getState() == State.BEFORE_QUERY_EXECUTE)
            {
                setup();
            }
        }
    }

    private void setup()
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }
        Optional<IPayloadbuilderQueryFileState> state = queryFile.getQueryFileState(IPayloadbuilderQueryFileState.class);
        if (state.isPresent())
        {
            IQuerySession session = state.get()
                    .getSession();
            IJdbcConnection connection = (IJdbcConnection) propertiesComponent.connections.getSelectedItem();
            String url = "";
            String driverClassName = "";
            if (connection != null)
            {
                url = connection.getJdbcURL();
                driverClassName = connection.getJdbcDriverClassName();
                session.setCatalogProperty(catalogAlias, JdbcCatalog.USERNAME, connection.getUsername());
                session.setCatalogProperty(catalogAlias, JdbcCatalog.PASSWORD, connection.getPassword());
            }
            String database = (String) propertiesComponent.databases.getSelectedItem();
            session.setCatalogProperty(catalogAlias, JdbcCatalog.DRIVER_CLASSNAME, driverClassName);
            session.setCatalogProperty(catalogAlias, JdbcCatalog.URL, url);
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
        Optional<IPayloadbuilderQueryFileState> state = queryFile.getQueryFileState(IPayloadbuilderQueryFileState.class);
        if (state.isPresent())
        {
            IQuerySession session = state.get()
                    .getSession();
            String url = session.getCatalogProperty(catalogAlias, JdbcCatalog.URL);
            String database = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE);

            MutableObject<IJdbcConnection> connectionToSelect = new MutableObject<>();
            MutableObject<String> databaseToSelect = new MutableObject<>();

            int size = connectionsModel.getSize();
            for (int i = 0; i < size; i++)
            {
                IJdbcConnection connection = connectionsModel.getElementAt(i);

                if (!equalsIgnoreCase(url, connection.getJdbcURL()))
                {
                    continue;
                }

                connectionToSelect.setValue(connection);

                List<String> databases = databasesByConnection.getOrDefault(connection, emptyList());

                int size2 = databases.size();
                for (int j = 0; j < size2; j++)
                {
                    if (equalsIgnoreCase(database, databases.get(j)))
                    {
                        databaseToSelect.setValue(databases.get(j));
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
    }

    /** Properties component. */
    private class PropertiesComponent extends JPanel
    {
        private static final String PROTOTYPE_CATALOG = "somedatabasewithalongname";
        private final JComboBox<IJdbcConnection> connections;
        private final JComboBox<String> databases;
        private final DefaultComboBoxModel<String> databasesModel = new DefaultComboBoxModel<>();
        private final JButton reload;

        PropertiesComponent()
        {
            setLayout(new GridBagLayout());

            connections = new JComboBox<>(new ConnectionsSelectionModel());
            databases = new JComboBox<>(databasesModel);
            reload = new JButton("Reload");
            reload.addActionListener(l ->
            {
                IJdbcConnection connection = (IJdbcConnection) connections.getSelectedItem();
                if (connection == null)
                {
                    return;
                }
                reload(connection, false);
            });

            connections.addItemListener(l ->
            {
                IJdbcConnection connection = (IJdbcConnection) l.getItem();
                populateDatabases(connection);
            });

            componentFactory.enableAutoCompletion(databases);
            databases.setPrototypeDisplayValue(PROTOTYPE_CATALOG);
            databases.addItemListener(l ->
            {
                setup();
            });
            // CSOFF
            databases.setMaximumRowCount(25);
            // CSON
            add(new JLabel("Server"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 1, 3), 0, 0));
            add(connections, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 1, 0), 0, 0));
            add(new JLabel("Database"), new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 1, 3), 0, 0));
            add(databases, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 1, 0), 0, 0));
            add(reload, new GridBagConstraints(1, 2, 1, 1, 1.0, 1.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
            // CSOFF
            setPreferredSize(new Dimension(240, 75));
            // CSON
        }

        private void reload(IJdbcConnection connection, boolean suppressError)
        {
            CredentialsResult credentialsResult = Utils.ensureCredentials(connection);
            if (credentialsResult == CredentialsResult.CANCELLED)
            {
                return;
            }

            Thread t = new Thread(() ->
            {
                try (Connection sqlConnection = connectionFactory.getConnection(connection))
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
                    databases.sort((a, b) -> a.compareTo(b));
                    databasesByConnection.put(connection, databases);
                    SwingUtilities.invokeLater(() -> populateDatabases(connection));
                }
                catch (Exception e)
                {
                    // CLear password if they were provided on this call
                    if (credentialsResult == CredentialsResult.CREDENTIALS_PROVIDED)
                    {
                        connection.setPassword(null);
                    }

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
            });
            t.start();
        }

        private void populateDatabases(IJdbcConnection connection)
        {
            databasesModel.removeAllElements();
            for (String database : databasesByConnection.getOrDefault(connection, emptyList()))
            {
                databasesModel.addElement(database);
            }
        }
    }

    private class ConnectionsSelectionModel extends DefaultComboBoxModel<IJdbcConnection>
    {
        private Object selectedItem;

        @Override
        public Object getSelectedItem()
        {
            return selectedItem;
        }

        @Override
        public void setSelectedItem(Object connection)
        {
            // Guard against weird values sent in
            if (connection != null
                    && !(connection instanceof IJdbcConnection))
            {
                return;
            }

            if ((selectedItem != null
                    && !selectedItem.equals(connection))
                    || selectedItem == null
                            && connection != null)
            {
                selectedItem = connection;
                fireContentsChanged(this, -1, -1);
            }
        }

        @Override
        public int getSize()
        {
            return connectionsModel.getSize();
        }

        @Override
        public IJdbcConnection getElementAt(int index)
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
