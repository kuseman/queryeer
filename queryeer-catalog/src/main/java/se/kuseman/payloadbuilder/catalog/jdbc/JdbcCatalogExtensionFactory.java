package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IQueryFileProvider;

/** Factory for {@link JdbcCatalogExtension}. */
class JdbcCatalogExtensionFactory implements ICatalogExtensionFactory
{
    private final JdbcConnectionsModel connectionsModel;
    private final IQueryFileProvider queryFileProvider;
    private final IIconFactory iconFactory;

    public JdbcCatalogExtensionFactory(IQueryFileProvider queryFileProvider, JdbcConnectionsModel connectionsModel, IIconFactory iconFactory)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.iconFactory = requireNonNull(iconFactory, "iconFactory");
    }

    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        return new JdbcCatalogExtension(connectionsModel, queryFileProvider, catalogAlias, iconFactory);
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
