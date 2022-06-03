package com.queryeer.provider.payloadbuilder.catalog.es;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IComponentFactory;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

/** Factory for {@link ESCatalogExtension}. */
class ESCatalogExtensionFactory implements ICatalogExtensionFactory
{
    private final IQueryFileProvider queryFileProvider;
    private final IComponentFactory componentFactory;
    private final ESConnectionsModel connectionsModel;
    private IEventBus eventBus;

    public ESCatalogExtensionFactory(IQueryFileProvider queryFileProvider, IComponentFactory componentFactory, IEventBus eventBus, ESConnectionsModel connectionsModel)
    {
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.componentFactory = requireNonNull(componentFactory, "componentFactory");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.eventBus = requireNonNull(eventBus, "eventBus");
    }

    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        ESCatalogExtension extension = new ESCatalogExtension(queryFileProvider, componentFactory, connectionsModel, catalogAlias);
        eventBus.register(extension);
        return extension;
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
