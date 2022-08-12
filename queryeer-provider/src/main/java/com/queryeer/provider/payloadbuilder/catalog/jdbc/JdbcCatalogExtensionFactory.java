package com.queryeer.provider.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IComponentFactory;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.jdbc.IConnectionFactory;
import com.queryeer.jdbc.IJdbcConnectionListModel;

/** Factory for {@link JdbcCatalogExtension}. */
class JdbcCatalogExtensionFactory implements ICatalogExtensionFactory
{
    private IJdbcConnectionListModel connectionsModel;
    private IQueryFileProvider queryFileProvider;
    private IComponentFactory componentFactory;
    private IConnectionFactory connectionFactory;

    public JdbcCatalogExtensionFactory(IConnectionFactory connectionFactory, IQueryFileProvider queryFileProvider, IComponentFactory componentFactory, IJdbcConnectionListModel connectionsModel)
    {
        this.connectionFactory = requireNonNull(connectionFactory, "connectionFactory");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.componentFactory = requireNonNull(componentFactory, "componentFactory");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
    }

    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        return new JdbcCatalogExtension(connectionsModel, connectionFactory, queryFileProvider, componentFactory, catalogAlias);
    }

    @Override
    public String getDefaultAlias()
    {
        return "jdbc";
    }

    @Override
    public int order()
    {
        return 1;
    }
}
