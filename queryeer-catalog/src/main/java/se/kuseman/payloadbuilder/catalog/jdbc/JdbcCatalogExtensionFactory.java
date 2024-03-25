package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IQueryFileProvider;

/** Factory for {@link JdbcCatalogExtension}. */
class JdbcCatalogExtensionFactory implements ICatalogExtensionFactory
{
    private final JdbcConnectionsModel connectionsModel;
    private final IQueryFileProvider queryFileProvider;
    private final IEventBus eventBus;
    private final IIconFactory iconFactory;

    public JdbcCatalogExtensionFactory(IQueryFileProvider queryFileProvider, IEventBus eventBus, JdbcConnectionsModel connectionsModel, IIconFactory iconFactory)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.iconFactory = requireNonNull(iconFactory, "iconFactory");
    }

    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        JdbcCatalogExtension extension = new JdbcCatalogExtension(connectionsModel, queryFileProvider, catalogAlias, iconFactory);
        eventBus.register(extension);
        return extension;
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
