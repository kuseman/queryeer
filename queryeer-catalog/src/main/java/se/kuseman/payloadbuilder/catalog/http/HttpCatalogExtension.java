package se.kuseman.payloadbuilder.catalog.http;

import com.queryeer.api.extensions.catalog.ICatalogExtension;

import se.kuseman.payloadbuilder.api.catalog.Catalog;

class HttpCatalogExtension implements ICatalogExtension
{
    private static final HttpCatalog CATALOG = new HttpCatalog();

    @Override
    public String getTitle()
    {
        return "HTTP Catalog";
    }

    @Override
    public Catalog getCatalog()
    {
        return CATALOG;
    }
}