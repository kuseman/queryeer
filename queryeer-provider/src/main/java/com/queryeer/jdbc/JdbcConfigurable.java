package com.queryeer.jdbc;

import static java.util.Objects.requireNonNull;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.queryeer.api.component.IListPropertiesComponent;
import com.queryeer.api.service.IComponentFactory;
import com.queryeer.jdbc.JdbcConnectionModel.JdbcConnection;

/** Jdbc configurable */
class JdbcConfigurable implements IJdbcConfigurable
{
    private final JdbcConnectionModel connectionsModel;
    private final List<Consumer<Boolean>> dirstyStateConsumers = new ArrayList<>();
    private final IComponentFactory componentFactory;
    private IListPropertiesComponent<IJdbcConnection> configComponent;

    JdbcConfigurable(IComponentFactory componentFactory, JdbcConnectionModel connectionsModel)
    {
        this.componentFactory = requireNonNull(componentFactory, "componentFactory");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
    }

    JdbcConnectionModel getConnectionsModel()
    {
        return connectionsModel;
    }

    @Override
    public Component getComponent()
    {
        if (configComponent == null)
        {
            configComponent = componentFactory.createListPropertiesComponent(JdbcConnection.class, this::notifyDirty, this::newConnection);
            configComponent.init(connectionsModel.copyConnections());
        }
        return configComponent.getComponent();
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
        connectionsModel.save();
    }

    @Override
    public String getTitle()
    {
        return "Jdbc Connections";
    }

    @Override
    public String groupName()
    {
        return "Jdbc";
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirstyStateConsumers.add(consumer);
    }

    private JdbcConnection newConnection()
    {
        JdbcConnection connection = new JdbcConnection();
        connection.setName("New Connection");
        return connection;
    }

    private void notifyDirty(boolean dirty)
    {
        dirstyStateConsumers.forEach(c -> c.accept(dirty));
    }
}
