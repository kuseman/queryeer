package se.kuseman.payloadbuilder.catalog.kafka;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.Strings.CI;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataListener;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.AutoCompletionComboBox;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.engine.IQueryEngine.IState.MetaParameter;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtensionView;
import com.queryeer.api.extensions.payloadbuilder.IPayloadbuilderState;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.CredentialsException;

/** Queryeer extension for {@link KafkaCatalog}. */
class KafkaCatalogExtension implements ICatalogExtension
{
    static final Catalog CATALOG = new KafkaCatalog();

    private final IQueryFileProvider queryFileProvider;
    private final KafkaConnectionsModel connectionsModel;
    private final String catalogAlias;

    private QuickPropertiesPanel quickPropertiesPanel;

    KafkaCatalogExtension(IQueryFileProvider queryFileProvider, KafkaConnectionsModel connectionsModel, String catalogAlias)
    {
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.catalogAlias = catalogAlias;
    }

    @Override
    public String getTitle()
    {
        return CATALOG.getName();
    }

    @Override
    public Catalog getCatalog()
    {
        return CATALOG;
    }

    @Override
    public Class<? extends IConfigurable> getConfigurableClass()
    {
        return KafkaConnectionsConfigurable.class;
    }

    @Override
    public boolean hasQuickPropertieComponent()
    {
        return true;
    }

    @Override
    public Component getQuickPropertiesComponent()
    {
        if (quickPropertiesPanel == null)
        {
            quickPropertiesPanel = new QuickPropertiesPanel();
        }
        return quickPropertiesPanel;
    }

    @Override
    public List<MetaParameter> getMetaParameters(IQuerySession querySession, boolean testData)
    {
        String bootstrapServers = "localhost:9092";
        String schemaRegistryUrl = "";
        String securityProtocol = KafkaConnection.SecurityProtocol.PLAINTEXT.name();
        String saslMechanism = KafkaConnection.SaslMechanism.PLAIN.name();
        String topic = "my_topic";

        if (!testData)
        {
            bootstrapServers = querySession.getCatalogProperty(catalogAlias, KafkaCatalog.BOOTSTRAP_SERVERS)
                    .valueAsString(0);
            schemaRegistryUrl = querySession.getCatalogProperty(catalogAlias, KafkaCatalog.SCHEMA_REGISTRY_URL)
                    .valueAsString(0);
            securityProtocol = querySession.getCatalogProperty(catalogAlias, KafkaCatalog.SECURITY_PROTOCOL)
                    .valueAsString(0);
            saslMechanism = querySession.getCatalogProperty(catalogAlias, KafkaCatalog.SASL_MECHANISM)
                    .valueAsString(0);
            topic = querySession.getCatalogProperty(catalogAlias, KafkaCatalog.TOPIC)
                    .valueAsString(0);
        }

        return List.of(new MetaParameter(KafkaCatalog.BOOTSTRAP_SERVERS, bootstrapServers, "Kafka bootstrap servers"),
                new MetaParameter(KafkaCatalog.SCHEMA_REGISTRY_URL, schemaRegistryUrl, "Schema registry url"),
                new MetaParameter(KafkaCatalog.SECURITY_PROTOCOL, securityProtocol, "Kafka security protocol"), new MetaParameter(KafkaCatalog.SASL_MECHANISM, saslMechanism, "Kafka sasl mechanism"),
                new MetaParameter(KafkaCatalog.TOPIC, topic, "Default topic"));
    }

    @Override
    public ExceptionAction handleException(IQuerySession querySession, CatalogException exception)
    {
        if (!(exception instanceof CredentialsException))
        {
            return ExceptionAction.NONE;
        }

        KafkaConnection selectedConnection = (KafkaConnection) quickPropertiesPanel.connections.getSelectedItem();
        KafkaConnection connection = connectionsModel.findConnection(querySession, catalogAlias);

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

        if (connection != null)
        {
            connection.setRuntimeSaslJaasPassword(null);
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

    void setupConnection(KafkaConnection connection)
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }
        if (connection != null
                && queryFile.getEngineState() instanceof IPayloadbuilderState state)
        {
            connection.setup(state.getQuerySession(), catalogAlias);
            state.getQuerySession()
                    .setCatalogProperty(catalogAlias, KafkaCatalog.TOPIC, (String) null);
        }
    }

    void setupTopic(String topic)
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            return;
        }
        if (queryFile.getEngineState() instanceof IPayloadbuilderState state)
        {
            state.getQuerySession()
                    .setCatalogProperty(catalogAlias, KafkaCatalog.TOPIC, !isBlank(topic) ? topic
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

        IQuerySession session = state.getQuerySession();

        KafkaConnection connectionToSet = connectionsModel.findConnection(session, catalogAlias);
        boolean sessionHasConnection = connectionToSet != null;
        String topic = session.getCatalogProperty(catalogAlias, KafkaCatalog.TOPIC)
                .valueAsString(0);

        if (connectionToSet == null
                && connectionsModel.getSize() > 0)
        {
            connectionToSet = connectionsModel.getElementAt(0);
        }

        if (!sessionHasConnection
                && connectionToSet != null)
        {
            connectionToSet.setup(session, catalogAlias);
        }

        String topicToSet = null;
        if (connectionToSet != null)
        {
            List<String> topics = getIfNull(connectionsModel.getTopics(connectionToSet, false, false), emptyList());
            for (String item : topics)
            {
                if (CI.equals(item, topic))
                {
                    topicToSet = item;
                    break;
                }
            }
        }

        final KafkaConnection connectionFinal = connectionToSet;
        final String topicFinal = topicToSet;

        SwingUtilities.invokeLater(() ->
        {
            quickPropertiesPanel.suppressEvents = true;
            try
            {
                quickPropertiesPanel.connections.getModel()
                        .setSelectedItem(connectionFinal);
                quickPropertiesPanel.populateTopics(connectionFinal);
                quickPropertiesPanel.topics.getModel()
                        .setSelectedItem(topicFinal);
                quickPropertiesPanel.updateAuthStatus(connectionFinal);
            }
            finally
            {
                quickPropertiesPanel.suppressEvents = false;
            }
        });
    }

    /** Quick properties panel. */
    class QuickPropertiesPanel extends JPanel implements ICatalogExtensionView
    {
        private static final String PROTOTYPE_TOPIC = "topic-with-a-very-long-name-to-display";
        private final DefaultComboBoxModel<String> topicsModel = new DefaultComboBoxModel<>();
        private final JLabel authStatus = new JLabel();
        private final JButton reload = new JButton("Reload");
        boolean suppressEvents;

        final JComboBox<KafkaConnection> connections;
        final JComboBox<String> topics;

        QuickPropertiesPanel()
        {
            setLayout(new GridBagLayout());

            connections = new JComboBox<>(new ConnectionsSelectionModel());
            connections.setRenderer(new DefaultListCellRenderer()
            {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof KafkaConnection connection)
                    {
                        if (!connection.isEnabled())
                        {
                            setText("<html><i>" + connection + "</i></html>");
                        }
                        else
                        {
                            setText(connection.toString());
                        }
                    }
                    return this;
                }
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
                    KafkaConnection connection = (KafkaConnection) connections.getSelectedItem();
                    topicsModel.removeAllElements();
                    if (connection != null)
                    {
                        connectionsModel.prepare(connection, true);
                        setupConnection(connection);
                        loadTopics(connection, false);
                    }
                    updateAuthStatus(connection);
                }
                finally
                {
                    suppressEvents = false;
                }
            });

            topics = new JComboBox<>(topicsModel);
            topics.setPrototypeDisplayValue(PROTOTYPE_TOPIC);
            topics.setMaximumRowCount(25);
            AutoCompletionComboBox.enable(topics);
            topics.addItemListener(l ->
            {
                if (suppressEvents)
                {
                    return;
                }
                setupTopic((String) topics.getSelectedItem());
            });

            connectionsModel.registerReloadButton(reload);
            reload.addActionListener(l ->
            {
                KafkaConnection connection = (KafkaConnection) connections.getSelectedItem();
                if (connection == null
                        || !connection.isEnabled())
                {
                    return;
                }
                if (!connectionsModel.prepare(connection, false))
                {
                    return;
                }

                setupConnection(connection);
                loadTopics(connection, true);
            });

            add(new JLabel("Connection"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 1, 3), 0, 0));
            add(connections, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 1, 0), 0, 0));
            add(new JLabel("Topic"), new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 1, 3), 0, 0));
            add(topics, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 1, 0), 0, 0));
            add(authStatus, new GridBagConstraints(0, 2, 1, 1, 0.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(3, 0, 0, 0), 0, 0));
            add(reload, new GridBagConstraints(1, 2, 1, 1, 1.0, 1.0, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
            setPreferredSize(new Dimension(240, 75));
        }

        @Override
        public void afterExecute(IQueryFile queryFile)
        {
            if (queryFileProvider.getCurrentFile() == queryFile)
            {
                KafkaCatalogExtension.this.update(queryFile);
            }
        }

        @Override
        public void focus(IQueryFile queryFile)
        {
            KafkaCatalogExtension.this.update(queryFile);
        }

        void updateAuthStatus(KafkaConnection connection)
        {
            if (connection == connections.getSelectedItem())
            {
                boolean hasCredentials = connection != null
                        && connection.hasCredentials();
                if (connection != null
                        && connection.isSaslEnabled())
                {
                    authStatus.setText(hasCredentials ? "Authenticated"
                            : "Credentials required");
                }
                else
                {
                    authStatus.setText("");
                }
            }
        }

        void populateTopics(KafkaConnection connection)
        {
            topicsModel.removeAllElements();
            if (connection == null
                    || connection.getTopics() == null)
            {
                return;
            }
            for (String topic : connection.getTopics())
            {
                topicsModel.addElement(topic);
            }
        }

        private void loadTopics(KafkaConnection connection, boolean forceReload)
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
                    connectionsModel.getTopics(connection, forceReload, true);
                    SwingUtilities.invokeLater(() ->
                    {
                        Object selected = topics.getSelectedItem();
                        populateTopics(connection);
                        topics.setSelectedItem(selected);
                        updateAuthStatus(connection);
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

    private class ConnectionsSelectionModel extends DefaultComboBoxModel<KafkaConnection>
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
        public KafkaConnection getElementAt(int index)
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
