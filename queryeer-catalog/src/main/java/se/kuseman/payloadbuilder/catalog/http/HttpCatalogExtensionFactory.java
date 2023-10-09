package se.kuseman.payloadbuilder.catalog.http;

import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtensionFactory;

class HttpCatalogExtensionFactory implements ICatalogExtensionFactory
{
    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        return new HttpCatalogExtension();
    }

    @Override
    public String getTitle()
    {
        return HttpCatalogExtension.TITLE;
    }

    @Override
    public String getDefaultAlias()
    {
        return "http";
    }
}
