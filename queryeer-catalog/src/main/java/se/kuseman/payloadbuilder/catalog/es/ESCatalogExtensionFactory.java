package se.kuseman.payloadbuilder.catalog.es;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IQueryFileProvider;

/** Factory for {@link ESCatalogExtension}. */
class ESCatalogExtensionFactory implements ICatalogExtensionFactory
{
    private final IQueryFileProvider queryFileProvider;
    private final ESConnectionsModel connectionsModel;

    public ESCatalogExtensionFactory(IQueryFileProvider queryFileProvider, ESConnectionsModel connectionsModel)
    {
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
    }

    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        return new ESCatalogExtension(queryFileProvider, connectionsModel, catalogAlias);
    }

    @Override
    public String getDefaultAlias()
    {
        return "es";
    }

    @Override
    public int order()
    {
        return 0;
    }
}
