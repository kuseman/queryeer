package com.queryeer.jdbc;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.queryeer.api.service.Inject;

@Inject
class JdbcDatabaseListModelFactory implements IJdbcDatabaseListModelFactory
{
    private Map<IJdbcConnection, IJdbcDatabaseListModel> models = new ConcurrentHashMap<>();
    private IConnectionFactory connectionFactory;

    JdbcDatabaseListModelFactory(IConnectionFactory connectionFactory)
    {
        this.connectionFactory = requireNonNull(connectionFactory, "connectionFactory");
    }

    @Override
    public IJdbcDatabaseListModel getModel(IJdbcConnection connection)
    {
        return models.computeIfAbsent(connection, c -> new JdbcDatabaseListModel(connectionFactory, connection));
    }
}
