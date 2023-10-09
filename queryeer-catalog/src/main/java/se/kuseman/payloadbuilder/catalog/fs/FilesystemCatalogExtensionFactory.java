package se.kuseman.payloadbuilder.catalog.fs;

import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtensionFactory;

/** SPI factory for {@link FilesystemCatalogExtension}. */
public class FilesystemCatalogExtensionFactory implements ICatalogExtensionFactory
{
    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        return new FilesystemCatalogExtension();
    }

    @Override
    public String getTitle()
    {
        return FilesystemCatalogExtension.CATALOG.getName();
    }

    @Override
    public String getDefaultAlias()
    {
        return "fs";
    }
}
