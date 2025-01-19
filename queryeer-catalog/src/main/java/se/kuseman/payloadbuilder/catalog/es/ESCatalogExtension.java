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
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataListener;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.AutoCompletionComboBox;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.engine.IQueryEngine.IState.MetaParameter;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtensionView;
import com.queryeer.api.extensions.payloadbuilder.ICompletionProvider;
import com.queryeer.api.extensions.payloadbuilder.IPayloadbuilderState;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IIconFactory.Provider;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.utils.CredentialUtils.Credentials;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.api.execution.UTF8String;
import se.kuseman.payloadbuilder.catalog.Common;
import se.kuseman.payloadbuilder.catalog.CredentialsException;
import se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Connection;
import se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Index;

/** Queryeer extension for {@link ESCatalog}. */
class ESCatalogExtension implements ICatalogExtension
{
    static final String TITLE = "Elasticsearch";
    static final ESCatalog CATALOG = new ESCatalog();

    private final IQueryFileProvider queryFileProvider;
    private final String catalogAlias;
    private final ESConnectionsModel connectionsModel;
    private final QuickPropertiesPanel quickPropertiesPanel;
    private final ESCompletionProvider completionProvider;
    private final IIconFactory iconFactory;

    ESCatalogExtension(IQueryFileProvider queryFileProvider, ESConnectionsModel connectionsModel, ESCompletionProvider completionProvider, String catalogAlias, IIconFactory iconFactory)
    {
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.catalogAlias = catalogAlias;
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.iconFactory = requireNonNull(iconFactory, "iconFactory");
        this.quickPropertiesPanel = new QuickPropertiesPanel();
        this.completionProvider = completionProvider;
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
    public List<MetaParameter> getMetaParameters(IQuerySession querySession, boolean testData)
    {
        String endpoint = "http://localhost:19200";
        String index = "filebeat*";
        if (!testData)
        {
            endpoint = querySession.getCatalogProperty(catalogAlias, ESCatalog.ENDPOINT_KEY)
                    .valueAsString(0);
            index = querySession.getCatalogProperty(catalogAlias, ESCatalog.INDEX_KEY)
                    .valueAsString(0);
        }

        //@formatter:off
        return List.of(
                new MetaParameter(ESCatalog.ENDPOINT_KEY, endpoint, "Endpoint to Elastic"),
                new MetaParameter(ESCatalog.INDEX_KEY, index, "Index pattern to query")
                );
        //@formatter:on
    }

    @Override
    public ExceptionAction handleException(IQuerySession querySession, CatalogException exception)
    {
        String catalogAlias = exception.getCatalogAlias();
        // Credentials exception thrown, ask for credentials
        if (exception instanceof CredentialsException)
        {
            Connection selectedConnection = (Connection) quickPropertiesPanel.connections.getSelectedItem();

            // Try to find the connection from session
            Connection connection = connectionsModel.findConnection(querySession, catalogAlias);

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
                String endpoint = querySession.getCatalogProperty(catalogAlias, ESCatalog.ENDPOINT_KEY)
                        .valueAsString(0);
                String username = querySession.getCatalogProperty(catalogAlias, ESCatalog.AUTH_USERNAME_KEY)
                        .valueAsString(0);
                Credentials credentials = connectionsModel.getCredentials(endpoint, username, false);
                if (credentials == null)
                {
                    return ExceptionAction.NONE;
                }

                querySession.setCatalogProperty(catalogAlias, ESCatalog.AUTH_USERNAME_KEY, credentials.getUsername());
                querySession.setCatalogProperty(catalogAlias, ESCatalog.AUTH_PASSWORD_KEY, credentials.getPassword());
                return ExceptionAction.RERUN;
            }

            // Clear the runtime password since we have a credentials exception
            connection.setRuntimeAuthPassword(null);
            // Prepare connection and rerun query
            if (!connectionsModel.prepare(connection, false))
            {
                return ExceptionAction.NONE;
            }

            connection.setup(querySession, catalogAlias);
            quickPropertiesPanel.updateAuthStatus(connection);
            return ExceptionAction.RERUN;
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

    @Override
    public ICompletionProvider getAutoCompletionProvider()
    {
        return completionProvider;
    }

    void setupConnection(Connection connection)
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
            }
        }
    }

    void setupIndex(Index index)
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }
        IPayloadbuilderState state = (IPayloadbuilderState) queryFile.getEngineState();
        if (state != null)
        {
            state.getQuerySession()
                    .setCatalogProperty(catalogAlias, ESCatalog.INDEX_KEY, index != null ? index.name
                            : null);
        }
    }

    void update(IQueryFile queryFile)
    {
        if (queryFile == null)
        {
            return;
        }
        IPayloadbuilderState state = (IPayloadbuilderState) queryFile.getEngineState();
        if (state == null)
        {
            return;
        }

        IQuerySession querySession = state.getQuerySession();

        // Find connection from session
        Connection connectionToSet = connectionsModel.findConnection(querySession, catalogAlias);

        String index = querySession.getCatalogProperty(catalogAlias, ESCatalog.INDEX_KEY)
                .valueAsString(0);
        Object password = querySession.getCatalogProperty(catalogAlias, ESCatalog.AUTH_PASSWORD_KEY)
                .valueAsObject(0);

        Index indexToSet = null;
        if (connectionToSet != null)
        {
            List<Index> indices = defaultIfNull(connectionsModel.getIndices(connectionToSet, false), emptyList());
            for (Index idx : indices)
            {
                if (equalsIgnoreCase(index, idx.name))
                {
                    indexToSet = idx;
                    break;
                }
            }
        }

        if (connectionToSet == null
                && connectionsModel.getSize() > 0)
        {
            connectionToSet = connectionsModel.getElementAt(0);
        }
        // If we have a connection that requires credentials and doesn't have any and there is a password
        // in session, set it to the connection
        // Use case for this is when there are two different connections that share password then we can
        // set the password from the other connection, this might turn up wrong but then a credentials input will popup
        // if it's correct we saved the user from a password input
        else if (connectionToSet != null
                && !connectionToSet.hasCredentials()
                && password != null)
        {
            char[] pass = null;
            if (password instanceof char[])
            {
                pass = (char[]) password;
            }
            else if (password instanceof UTF8String
                    || password instanceof String)
            {
                pass = String.valueOf(password)
                        .toCharArray();
            }

            if (pass != null)
            {
                connectionToSet.setRuntimeAuthPassword(pass);
            }
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
            final Index tempB = indexToSet;

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
    class QuickPropertiesPanel extends JPanel implements ICatalogExtensionView
    {
        private final Icon lock = iconFactory.getIcon(Provider.FONTAWESOME, "LOCK");
        private final Icon unlock = iconFactory.getIcon(Provider.FONTAWESOME, "UNLOCK");

        private final Connection connectionPrototype = Connection.of("http://elasticsearch.domain.internal.com");
        private final Index indexPrototype = new Index("somelongindexname");
        private final DefaultComboBoxModel<Index> indicesModel = new DefaultComboBoxModel<>();
        private final JButton reloadIndices;
        private final JLabel authStatus;
        private boolean suppressSetupIndex = false;
        final JComboBox<Connection> connections;
        final JComboBox<Index> indices;

        QuickPropertiesPanel()
        {
            setLayout(new GridBagLayout());

            // CSOFF
            authStatus = new JLabel();
            add(new JLabel("Endpoint"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 0, 5), 0, 0));
            connections = new JComboBox<>(new ConnectionsSelectionModel());
            connections.addItemListener(l ->
            {
                Connection connection = (Connection) connections.getSelectedItem();
                indicesModel.removeAllElements();
                if (connection != null)
                {
                    // Prepare connection silent
                    connectionsModel.prepare(connection, true);
                    setupConnection(connection);
                    loadIndices(connection, false);
                }

                updateAuthStatus(connection);
            });
            connections.setPrototypeDisplayValue(connectionPrototype);
            connections.setMaximumRowCount(25);
            AutoCompletionComboBox.enable(connections);
            add(connections, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

            add(new JLabel("Index"), new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 0, 5), 0, 0));
            indices = new JComboBox<>(indicesModel);
            indices.addItemListener(l ->
            {
                if (!suppressSetupIndex)
                {
                    Object item = quickPropertiesPanel.indices.getSelectedItem();
                    if (item instanceof Index)
                    {
                        setupIndex((Index) item);
                    }
                }
            });

            indices.setPrototypeDisplayValue(indexPrototype);
            indices.setMaximumRowCount(25);
            AutoCompletionComboBox.enable(indices);
            add(indices, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

            add(authStatus, new GridBagConstraints(0, 2, 1, 1, 0.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(3, 0, 0, 0), 0, 0));

            reloadIndices = new JButton("Reload");
            connectionsModel.registerReloadButton(reloadIndices);
            reloadIndices.addActionListener(l ->
            {
                Connection connection = (Connection) connections.getSelectedItem();
                if (connection == null)
                {
                    return;
                }

                // Preparation aborted, don't load indices
                if (!connectionsModel.prepare(connection, false))
                {
                    return;
                }

                loadIndices(connection, true);
                setupConnection(connection);
                updateAuthStatus(connection);
            });
            add(reloadIndices, new GridBagConstraints(1, 2, 1, 1, 1.0, 1.0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

            setPreferredSize(new Dimension(240, 75));
            // CSON
        }

        @Override
        public void afterExecute(IQueryFile queryFile)
        {
            // Update quick properties with changed properties from query session
            // change file is the currently opened one
            if (queryFileProvider.getCurrentFile() == queryFile)
            {
                ESCatalogExtension.this.update(queryFile);
            }
        }

        @Override
        public void focus(IQueryFile queryFile)
        {
            ESCatalogExtension.this.update(queryFile);
        }

        private void updateAuthStatus(Connection connection)
        {
            // Only update if the provided connection is the selected one
            if (connection == connections.getSelectedItem())
            {
                SwingUtilities.invokeLater(() ->
                {
                    authStatus.setIcon(null);
                    authStatus.setToolTipText(null);
                    if (connection != null
                            && connection.getAuthType() != AuthType.NONE)
                    {
                        boolean hasCredentials = connection.hasCredentials();
                        authStatus.setIcon(hasCredentials ? unlock
                                : lock);
                        authStatus.setToolTipText(hasCredentials ? null
                                : Common.AUTH_STATUS_LOCKED_TOOLTIP);
                    }
                });
            }
        }

        private void loadIndices(Connection connection, boolean forceReload)
        {
            if (!connection.hasCredentials())
            {
                return;
            }

            IQueryFile currentFile = queryFileProvider.getCurrentFile();
            Runnable load = () ->
            {
                try
                {
                    final List<Index> indices = defaultIfNull(connectionsModel.getIndices(connection, forceReload), emptyList());
                    SwingUtilities.invokeLater(() ->
                    {
                        suppressSetupIndex = true;
                        final Object selectedIndex = quickPropertiesPanel.indices.getSelectedItem();
                        quickPropertiesPanel.indicesModel.removeAllElements();
                        for (Index index : indices)
                        {
                            quickPropertiesPanel.indicesModel.addElement(index);
                        }
                        quickPropertiesPanel.indices.setSelectedItem(selectedIndex);
                        suppressSetupIndex = false;
                    });
                }
                catch (Exception e)
                {
                    if (currentFile != null)
                    {
                        e.printStackTrace(currentFile.getMessagesWriter());
                        currentFile.focusMessages();
                    }
                    else
                    {
                        e.printStackTrace();
                    }
                }
            };

            new Thread(load).start();
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
