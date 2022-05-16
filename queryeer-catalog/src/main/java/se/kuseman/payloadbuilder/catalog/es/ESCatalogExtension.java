package se.kuseman.payloadbuilder.catalog.es;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataListener;

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
import se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Connection;

/** Queryeer extension for {@link ESCatalog}. */
class ESCatalogExtension implements ICatalogExtension
{
    private static final String TITLE = "Elasticsearch";
    private static final ESCatalog CATALOG = new ESCatalog();

    private final IQueryFileProvider queryFileProvider;
    private final String catalogAlias;
    private final ESConnectionsModel connectionsModel;
    private final QuickPropertiesPanel quickPropertiesPanel;

    ESCatalogExtension(IQueryFileProvider queryFileProvider, ESConnectionsModel connectionsModel, String catalogAlias)
    {
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.catalogAlias = catalogAlias;
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.quickPropertiesPanel = new QuickPropertiesPanel();
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
    public boolean hasQuickPropertieComponent()
    {
        return true;
    }

    @Override
    public ExceptionAction handleException(IQuerySession querySession, CatalogException exception)
    {
        String catalogAlias = exception.getCatalogAlias();
        // Credentials exception thrown, ask for credentials
        if (exception instanceof CredentialsException)
        {
            Connection connection = (Connection) quickPropertiesPanel.connections.getSelectedItem();
            String endpoint = querySession.getCatalogProperty(catalogAlias, ESCatalog.ENDPOINT_KEY);

            boolean isSelectedConnection = connection != null
                    && equalsIgnoreCase(endpoint, connection.endpoint);

            String connectionDescription = isSelectedConnection ? connection.toString()
                    : endpoint;
            String prefilledUsername = isSelectedConnection ? connection.authUsername
                    : querySession.getCatalogProperty(catalogAlias, ESCatalog.AUTH_USERNAME_KEY);

            Credentials credentials = CredentialUtils.getCredentials(connectionDescription, prefilledUsername);
            if (credentials != null)
            {
                // CSOFF
                if (isSelectedConnection)
                // CSON
                {
                    // Only change password on if the endpoint is the selected connection
                    connection.authPassword = credentials.getPassword();

                    // Utilize connection to reload databases if not already done
                    // CSOFF
                    if (connection.indices == null)
                    // CSON
                    {
                        quickPropertiesPanel.loadIndices(connection, true);
                    }
                }

                querySession.setCatalogProperty(catalogAlias, ESCatalog.AUTH_USERNAME_KEY, credentials.getUsername());
                querySession.setCatalogProperty(catalogAlias, ESCatalog.AUTH_PASSWORD_KEY, credentials.getPassword());
                return ExceptionAction.RERUN;
            }
        }

        return ExceptionAction.NONE;
    }

    @Override
    public Component getQuickPropertiesComponent()
    {
        return quickPropertiesPanel;
    }

    @Override
    public Class<? extends IConfigurable> getConfigurableClass()
    {
        return ESCatalogConfigurable.class;
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
        Connection connection = (Connection) quickPropertiesPanel.connections.getSelectedItem();
        if (connection != null)
        {
            connection.setup(session, catalogAlias);
        }
        String index = (String) quickPropertiesPanel.indices.getSelectedItem();
        session.setCatalogProperty(catalogAlias, ESCatalog.INDEX_KEY, index);
    }

    private void update()
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }
        IQuerySession querySession = queryFile.getSession();
        // Try to find correct connection to select if changed
        String endpoint = querySession.getCatalogProperty(catalogAlias, ESCatalog.ENDPOINT_KEY);
        String index = querySession.getCatalogProperty(catalogAlias, ESCatalog.INDEX_KEY);
        Connection connectionToSet = null;
        String indexToSet = null;

        int size = connectionsModel.getSize();
        for (int i = 0; i < size; i++)
        {
            Connection connection = connectionsModel.getElementAt(i);
            if (equalsIgnoreCase(connection.endpoint, endpoint))
            {
                connectionToSet = connection;
                List<String> indices = defaultIfNull(connectionsModel.getIndices(connection, false), emptyList());
                for (String idx : indices)
                {
                    if (equalsIgnoreCase(index, idx))
                    {
                        indexToSet = idx;
                        break;
                    }
                }

                if (indexToSet != null)
                {
                    break;
                }
            }
        }

        if (connectionToSet == null
                && connectionsModel.getSize() > 0)
        {
            connectionToSet = connectionsModel.getElementAt(0);
        }

        if (SwingUtilities.isEventDispatchThread())
        {
            quickPropertiesPanel.connections.getModel()
                    .setSelectedItem(connectionToSet);
            quickPropertiesPanel.indices.getModel()
                    .setSelectedItem(indexToSet);
        }
        else
        {
            final Connection tempA = connectionToSet;
            final String tempB = indexToSet;

            SwingUtilities.invokeLater(() ->
            {
                quickPropertiesPanel.connections.getModel()
                        .setSelectedItem(tempA);
                quickPropertiesPanel.indices.getModel()
                        .setSelectedItem(tempB);
            });
        }
    }

    /** Quick properties panel. */
    private class QuickPropertiesPanel extends JPanel
    {
        private final Connection connectionPrototype = Connection.of("http://elasticsearch.domain.internal.com");
        private final String indexPrototype = "somelongindexname";
        private final JComboBox<Connection> connections;
        private final JComboBox<String> indices;
        private final DefaultComboBoxModel<String> indicesModel = new DefaultComboBoxModel<>();
        private final JButton reloadIndices;

        QuickPropertiesPanel()
        {
            setLayout(new GridBagLayout());

            // CSOFF
            add(new JLabel("Endpoint"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 0, 5), 0, 0));
            connections = new JComboBox<>(new ConnectionsSelectionModel());
            connections.addItemListener(l ->
            {
                setup();
                Connection connection = (Connection) connections.getSelectedItem();
                indicesModel.removeAllElements();
                if (connection != null)
                {
                    loadIndices(connection, false);
                }
            });
            connections.setPrototypeDisplayValue(connectionPrototype);
            connections.setMaximumRowCount(25);
            AutoCompletionComboBox.enable(connections);
            add(connections, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

            add(new JLabel("Index"), new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 0, 5), 0, 0));
            indices = new JComboBox<>(indicesModel);
            indices.addItemListener(l ->
            {
                setup();
            });

            indices.setPrototypeDisplayValue(indexPrototype);
            indices.setMaximumRowCount(25);
            AutoCompletionComboBox.enable(indices);
            add(indices, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

            reloadIndices = new JButton("Reload");
            connectionsModel.registerReloadButton(reloadIndices);
            reloadIndices.addActionListener(l ->
            {
                Connection connection = (Connection) connections.getSelectedItem();
                if (connection == null)
                {
                    return;
                }
                loadIndices(connection, true);
            });
            add(reloadIndices, new GridBagConstraints(1, 2, 1, 1, 1.0, 1.0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

            setPreferredSize(new Dimension(240, 75));
            // CSON
        }

        private void loadIndices(Connection connection, boolean forceReload)
        {
            if (forceReload
                    && !connection.hasCredentials())
            {
                Credentials credentials = CredentialUtils.getCredentials(connection.toString(), connection.authUsername);
                if (credentials == null)
                {
                    return;
                }
                // connection.authUsername = credentials.getUsername();
                connection.authPassword = credentials.getPassword();
            }

            Thread thread = new Thread(() ->
            {
                try
                {
                    final List<String> indices = defaultIfNull(connectionsModel.getIndices(connection, forceReload), emptyList());
                    SwingUtilities.invokeLater(() ->
                    {
                        final Object selectedIndex = quickPropertiesPanel.indices.getSelectedItem();
                        quickPropertiesPanel.indicesModel.removeAllElements();
                        for (String index : indices)
                        {
                            quickPropertiesPanel.indicesModel.addElement(index);
                        }
                        quickPropertiesPanel.indices.setSelectedItem(selectedIndex);
                    });
                }
                catch (Exception e)
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
            });
            thread.start();
        }
    }

    private class ConnectionsSelectionModel extends DefaultComboBoxModel<ESConnectionsModel.Connection>
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
                    && !(connection instanceof Connection))
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
        public Connection getElementAt(int index)
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
