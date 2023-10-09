package com.queryeer.api.extensions.catalog;

import java.awt.Component;

import com.queryeer.api.extensions.IConfigurable;

import se.kuseman.payloadbuilder.api.catalog.Catalog;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;

/** Definition of a catalog extension */
public interface ICatalogExtension
{
    /** Get title of extension */
    String getTitle();

    /** Get the actual catalog implementation for this extension */
    Catalog getCatalog();

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
     * Get quick properties component. Will be showed in extensions side bar with quick properties. Ie. selected. database/index. Component can implement {@link ICatalogExtensionView} to get
     * notifications after execution etc.
     */
    default Component getQuickPropertiesComponent()
    {
        return null;
    }

    /** Return a provider for auto completions for this extension */
    default ICompletionProvider getAutoCompletionProvider()
    {
        return null;
    }

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
