package se.kuseman.payloadbuilder.catalog.fs;

import com.queryeer.api.extensions.catalog.ICatalogExtension;

import se.kuseman.payloadbuilder.api.catalog.Catalog;

/** Queryeer extension for {@link FilesystemCatalog}. */
class FilesystemCatalogExtension implements ICatalogExtension
{
    private static final Catalog CATALOG = new FilesystemCatalog();

    @Override
    public String getTitle()
    {
        return CATALOG.getName();
    }

    @Override
    public Catalog getCatalog()
    {
        return CATALOG;
    }
}
