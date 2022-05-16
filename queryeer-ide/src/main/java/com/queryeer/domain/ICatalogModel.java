package com.queryeer.domain;

import com.queryeer.api.extensions.catalog.ICatalogExtension;

/** Definition of a catalog model used by query files */
public interface ICatalogModel
{
    /** Get alias of model */
    String getAlias();

    /** Get the models catalog extension */
    ICatalogExtension getCatalogExtension();

    /** Returns if the catalog is disabled */
    boolean isDisabled();
}
