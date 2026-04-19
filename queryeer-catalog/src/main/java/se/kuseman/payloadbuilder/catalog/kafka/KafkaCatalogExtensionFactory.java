package se.kuseman.payloadbuilder.catalog.kafka;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtensionFactory;
import com.queryeer.api.service.IQueryFileProvider;

/** Extension factory for {@link KafkaCatalogExtension}. */
class KafkaCatalogExtensionFactory implements ICatalogExtensionFactory
{
    private final IQueryFileProvider queryFileProvider;
    private final KafkaConnectionsModel connectionsModel;

    KafkaCatalogExtensionFactory(IQueryFileProvider queryFileProvider, KafkaConnectionsModel connectionsModel)
    {
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
    }

    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        return new KafkaCatalogExtension(queryFileProvider, connectionsModel, catalogAlias);
    }

    @Override
    public String getTitle()
    {
        return KafkaCatalogExtension.CATALOG.getName();
    }

    @Override
    public String getDefaultAlias()
    {
        return "kafka";
    }

    @Override
    public int order()
    {
        return 2;
    }
}
