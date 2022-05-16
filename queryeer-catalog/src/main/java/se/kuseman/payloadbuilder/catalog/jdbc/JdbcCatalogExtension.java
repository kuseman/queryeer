package se.kuseman.payloadbuilder.catalog.jdbc;

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
import java.util.List;
import java.util.Objects;

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
import com.queryeer.api.component.AutoCompletionComboBox;
import com.queryeer.api.event.QueryFileChangedEvent;
import com.queryeer.api.event.QueryFileStateEvent;
import com.queryeer.api.event.QueryFileStateEvent.State;
import com.queryeer.api.event.Subscribe;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.utils.CredentialUtils;
import com.queryeer.api.utils.CredentialUtils.Credentials;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.session.IQuerySession;
import se.kuseman.payloadbuilder.catalog.CredentialsException;

/** Queryeer extension for {@link JdbcCatalog}. */
class JdbcCatalogExtension implements ICatalogExtension
{
    static final String TITLE = "Jdbc";
    private static final JdbcCatalog CATALOG = new JdbcCatalog();

    private final JdbcConnectionsModel connectionsModel;
    private final IQueryFileProvider queryFileProvider;
    private final String catalogAlias;
    private final PropertiesComponent propertiesComponent;

    JdbcCatalogExtension(JdbcConnectionsModel connectionsModel, IQueryFileProvider queryFileProvider, String catalogAlias)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
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
            JdbcConnectionsModel.Connection connection = (JdbcConnectionsModel.Connection) propertiesComponent.connections.getSelectedItem();
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
                    if (connection.getDatabases()
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
        IQuerySession session = queryFile.getSession();
        JdbcConnectionsModel.Connection connection = (JdbcConnectionsModel.Connection) propertiesComponent.connections.getSelectedItem();
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

    private void update()
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }
        IQuerySession session = queryFile.getSession();
        String url = session.getCatalogProperty(catalogAlias, JdbcCatalog.URL);
        String database = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE);

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
                if (equalsIgnoreCase(database, connection.getDatabases()
                        .get(j)))
                {
                    databaseToSelect.setValue(connection.getDatabases()
                            .get(j));
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
        private static final String PROTOTYPE_CATALOG = "somedatabasewithalongname";
        private final JComboBox<JdbcConnectionsModel.Connection> connections;
        private final JComboBox<String> databases;
        private final DefaultComboBoxModel<String> databasesModel = new DefaultComboBoxModel<>();
        private final JButton reload;

        PropertiesComponent()
        {
            setLayout(new GridBagLayout());

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
                reload(connection, false);
            });

            connections.addItemListener(l ->
            {
                JdbcConnectionsModel.Connection connection = (JdbcConnectionsModel.Connection) l.getItem();
                populateDatabases(connection);
            });

            AutoCompletionComboBox.enable(databases);
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

        private void reload(JdbcConnectionsModel.Connection connection, boolean suppressError)
        {
            if (!connection.hasCredentials())
            {
                Credentials credentials = CredentialUtils.getCredentials(connection.toString(), connection.getUsername());
                if (credentials == null)
                {
                    return;
                }
                // connection.username = credentials.getUsername();
                connection.setPassword(credentials.getPassword());
            }

            connectionsModel.setEnableRealod(false);
            Thread t = new Thread(() ->
            {
                try (Connection sqlConnection = CATALOG.getConnection(connection.getJdbcDriverClassName(), connection.getJdbcURL(), connection.getUsername(), new String(connection.getPassword()),
                        catalogAlias))
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
                    connection.setDatabases(databases);
                    SwingUtilities.invokeLater(() -> populateDatabases(connection));
                }
                catch (Exception e)
                {
                    connection.setPassword(null);
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
                    && !(connection instanceof JdbcConnectionsModel.Connection))
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
