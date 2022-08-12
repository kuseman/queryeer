package com.queryeer.provider.payloadbuilder;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;

import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

/** Quick properties for {@link PayloadbuilderQueryProvider} */
class QuickPropertiesView extends JPanel
{
    QuickPropertiesView(IEventBus eventBus, IQueryFileProvider queryFileProvider, List<CatalogModel> catalogs)
    {
        setLayout(new GridBagLayout());
        ButtonGroup buttonGroup = new ButtonGroup();
        int y = 0;
        Insets insets = new Insets(0, 0, 3, 0);
        for (CatalogModel catalog : catalogs)
        {
            if (catalog.isDisabled())
            {
                continue;
            }
            ICatalogExtension extension = catalog.getCatalogExtension();
            if (extension.getConfigurableClass() == null
                    && !extension.hasQuickPropertieComponent())
            {
                continue;
            }

            Component extensionView = new CatalogExtensionView(eventBus, queryFileProvider, extension, catalog.getAlias(), buttonGroup);
            add(extensionView, new GridBagConstraints(0, y++, 1, 1, 1, 0, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, insets, 0, 0));
        }
        add(new JPanel(), new GridBagConstraints(0, y, 1, 1, 1, 1, GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL, insets, 0, 0));
    }
}
