package se.kuseman.payloadbuilder.catalog.es;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IQueryFileProvider;

/** Factory for {@link ESCatalogExtension}. */
class ESCatalogExtensionFactory implements ICatalogExtensionFactory
{
    private final IQueryFileProvider queryFileProvider;
    private final ESConnectionsModel connectionsModel;
    private final IEventBus eventBus;
    private final IIconFactory iconFactory;

    ESCatalogExtensionFactory(IQueryFileProvider queryFileProvider, IEventBus eventBus, ESConnectionsModel connectionsModel, IIconFactory iconFactory)
    {
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.iconFactory = requireNonNull(iconFactory, "iconFactory");
    }

    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        ESCompletionProvider completionProvider = new ESCompletionProvider(connectionsModel);
        ESCatalogExtension extension = new ESCatalogExtension(queryFileProvider, connectionsModel, completionProvider, catalogAlias, iconFactory);
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
