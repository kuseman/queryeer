package com.queryeer;

/** Model for a catalog extension */
class CatalogExtensionModel
{
    private String alias;

    CatalogExtensionModel(String alias)
    {
        this.alias = alias;
    }

    String getAlias()
    {
        return alias;
    }

    void setAlias(String alias)
    {
        this.alias = alias;
    }
}
