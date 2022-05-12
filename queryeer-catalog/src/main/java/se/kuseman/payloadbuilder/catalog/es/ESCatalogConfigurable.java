package se.kuseman.payloadbuilder.catalog.es;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.queryeer.api.component.ListPropertiesComponent;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.service.IConfig;

import se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Connection;

/** ES configurable */
class ESCatalogConfigurable implements IConfigurable
{
    private static final String NAME = "se.kuseman.payloadbuilder.catalog.es.ESCatalogExtension";
    private static final String CONNECTIONS = "connections";
    private final ESConnectionsModel connectionsModel;
    private final List<Consumer<Boolean>> dirstyStateConsumers = new ArrayList<>();
    private final IConfig config;
    private ListPropertiesComponent<Connection> configComponent;

    ESCatalogConfigurable(IConfig config, ESConnectionsModel connectionsModel)
    {
        this.config = requireNonNull(config, "config");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        loadSettings();
    }

    ESConnectionsModel getConnectionsModel()
    {
        return connectionsModel;
    }

    @Override
    public Component getComponent()
    {
        if (configComponent == null)
        {
            configComponent = new ListPropertiesComponent<>(Connection.class, this::notifyDirty, this::connection);
            configComponent.init(connectionsModel.copyConnections());
        }
        return configComponent;
    }

    @Override
    public void revertChanges()
    {
        configComponent.init(connectionsModel.copyConnections());
    }

    @Override
    public void commitChanges()
    {
        connectionsModel.setConnections(configComponent.getResult());
        config.saveExtensionConfig(NAME, singletonMap(CONNECTIONS, connectionsModel.getConnections()));
    }

    @Override
    public String getTitle()
    {
        return "Elasticsearch";
    }

    @Override
    public String groupName()
    {
        return IConfigurable.CATALOG;
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirstyStateConsumers.add(consumer);
    }

    private Connection connection()
    {
        Connection connection = new Connection();
        connection.setEndpoint("http://localhost:9200");
        return connection;
    }

    private void notifyDirty(boolean dirty)
    {
        dirstyStateConsumers.forEach(c -> c.accept(dirty));
    }

    private void loadSettings()
    {
        Map<String, Object> settings = config.loadExtensionConfig(NAME);
        List<Connection> connections = ESOperator.MAPPER.convertValue(defaultIfNull(settings.get(CONNECTIONS), emptyList()), new TypeReference<List<Connection>>()
        {
        });
        if (connections == null)
        {
            return;
        }
        connectionsModel.setConnections(connections);
    }
}
