package com.queryeer.provider.payloadbuilder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.extensions.catalog.ICatalogExtension;

/** Payloadbuilder catalog model */
class CatalogModel
{
    @JsonProperty
    String alias;
    @JsonProperty
    String factory;
    @JsonProperty
    boolean disabled;

    @JsonIgnore
    ICatalogExtension catalogExtension;

    public String getAlias()
    {
        return alias;
    }

    public String getFactory()
    {
        return factory;
    }

    public boolean isDisabled()
    {
        return disabled;
    }

    public ICatalogExtension getCatalogExtension()
    {
        return catalogExtension;
    }
}
