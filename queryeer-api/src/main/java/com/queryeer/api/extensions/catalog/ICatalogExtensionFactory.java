package com.queryeer.api.extensions.catalog;

import com.queryeer.api.extensions.IExtension;

/** Definition of an extension factory that creates {@link ICatalogExtension}'s from a plugin */
public interface ICatalogExtensionFactory extends IExtension
{
    /**
     * Create extension
     *
     * @param catalogAlias The alias that this extension is bound to
     * @return Return created catalog extension
     */
    ICatalogExtension create(String catalogAlias);

    /** Return the default alias for this extension. Will be used as default if not explicitly configured */
    String getDefaultAlias();

    /**
     * Specifies order in which the extensions is added to Queryeer for this module. Only applicable if {@link ICatalogExtension#hasConfigComponent()} or
     * {@link ICatalogExtension#hasQuickPropertieComponent()} is true.
     */
    default int order()
    {
        return 0;
    }
}
