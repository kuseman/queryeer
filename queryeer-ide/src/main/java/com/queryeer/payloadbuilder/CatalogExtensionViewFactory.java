package com.queryeer.payloadbuilder;

import static java.util.Objects.requireNonNull;

import javax.swing.ButtonGroup;

import com.queryeer.api.extensions.Inject;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

/** Factory for creating catalog extensions */
@Inject
class CatalogExtensionViewFactory
{
    private final IEventBus eventBus;
    private final ButtonGroup defaultCatalogGroup = new ButtonGroup();
    private IQueryFileProvider queryFileProvider;

    CatalogExtensionViewFactory(IEventBus eventBus, IQueryFileProvider queryFileProvider)
    {
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
    }

    /** Create component for provided extension */
    CatalogExtensionView create(ICatalogExtension extensions, String catalogAlias)
    {
        return new CatalogExtensionView(eventBus, queryFileProvider, extensions, catalogAlias, defaultCatalogGroup);
    }
}
