package se.kuseman.payloadbuilder.catalog.http;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;

class HttpCatalogExtensionFactory implements ICatalogExtensionFactory
{
    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        return new HttpCatalogExtension();
    }

    @Override
    public String getDefaultAlias()
    {
        return "http";
    }
}
