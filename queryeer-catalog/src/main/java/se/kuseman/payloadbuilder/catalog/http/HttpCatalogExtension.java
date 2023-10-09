package se.kuseman.payloadbuilder.catalog.http;

import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;

import se.kuseman.payloadbuilder.api.catalog.Catalog;

class HttpCatalogExtension implements ICatalogExtension
{
    static final String TITLE = "HTTP Catalog";
    private static final HttpCatalog CATALOG = new HttpCatalog();

    @Override
    public String getTitle()
    {
        return TITLE;
    }

    @Override
    public Catalog getCatalog()
    {
        return CATALOG;
    }
}
