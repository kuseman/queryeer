package com.queryeer.provider.payloadbuilder.catalog.fs;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;

/** SPI factory for {@link FilesystemCatalogExtension}. */
public class FilesystemCatalogExtensionFactory implements ICatalogExtensionFactory
{
    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        return new FilesystemCatalogExtension();
    }

    @Override
    public String getDefaultAlias()
    {
        return "fs";
    }
}
