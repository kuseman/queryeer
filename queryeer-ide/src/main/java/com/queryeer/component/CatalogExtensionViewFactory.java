package com.queryeer.component;

import static java.util.Objects.requireNonNull;

import java.awt.Component;

import javax.swing.ButtonGroup;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

/** Factory for creating catalog extensions */
public class CatalogExtensionViewFactory
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
    public Component create(ICatalogExtension extensions, String catalogAlias)
    {
        return new CatalogExtensionView(eventBus, queryFileProvider, extensions, catalogAlias, defaultCatalogGroup);
    }
}
