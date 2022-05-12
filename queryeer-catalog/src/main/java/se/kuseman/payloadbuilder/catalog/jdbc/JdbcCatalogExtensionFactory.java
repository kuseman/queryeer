package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IQueryFileProvider;

/** Factory for {@link JdbcCatalogExtension}. */
class JdbcCatalogExtensionFactory implements ICatalogExtensionFactory
{
    private JdbcConnectionsModel connectionsModel;
    private IQueryFileProvider queryFileProvider;

    public JdbcCatalogExtensionFactory(IQueryFileProvider queryFileProvider, JdbcConnectionsModel connectionsModel)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
    }

    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        return new JdbcCatalogExtension(connectionsModel, queryFileProvider, catalogAlias);
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
