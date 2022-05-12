package com.queryeer.api.extensions.catalog;

import java.awt.Component;

import com.queryeer.api.extensions.IConfigurable;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.session.IQuerySession;

/** Definition of a catalog extension */
public interface ICatalogExtension
{
    /** Get title of extension */
    String getTitle();

    /**
     * Setup session before query is executed. Ie. set selected database/index etc.
     *
     * @param catalogAlias Alias that this extension/catalog has been given
     * @param querySession Current query session
     **/
    default void setup(String catalogAlias, IQuerySession querySession)
    {
    }

    /**
     * Update the extension based on the query session. Ie acting upon changed variables etc.
     *
     * @param catalogAlias Alias that this extension/catalog has been given
     * @param querySession Current query session
     **/
    default void update(String catalogAlias, IQuerySession querySession)
    {
    }

    /** Get the configurable class if this catalog extension supports configuration */
    default Class<? extends IConfigurable> getConfigurableClass()
    {
        return null;
    }

    /** Returns true if this extension has a quick properties component otherwise false */
    default boolean hasQuickPropertieComponent()
    {
        return false;
    }

    /**
     * Get quick properties component. Will be showed in extensions side bar with quick properties. Ie. selected. database/index.
     */
    default Component getQuickPropertiesComponent()
    {
        return null;
    }

    /** Get the actual catalog implementation for this extension */
    Catalog getCatalog();

    /**
     * Handle provided exception
     *
     * @param querySession Current query session
     * @param exception Exception to handle
     **/
    default ExceptionAction handleException(IQuerySession querySession, CatalogException exception)
    {
        return ExceptionAction.NONE;
    }

    /** Action that should be performed after handling of an Exception */
    enum ExceptionAction
    {
        /** Do nothing action */
        NONE,
        /** Re-run query. */
        RERUN;
    }
}
