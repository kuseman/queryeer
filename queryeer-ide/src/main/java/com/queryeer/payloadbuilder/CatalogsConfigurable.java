package com.queryeer.payloadbuilder;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IConfig;

/** Configurable for payloadbuilder module */
class CatalogsConfigurable implements IConfigurable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogsConfigurable.class);
    private static final String NAME = CatalogsConfigurable.class.getPackageName();
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final IConfig config;
    private final List<ICatalogExtensionFactory> catalogExtensionFactories;

    private PayloadbuilderConfig payloadbuilderConfig;
    private Component component;

    CatalogsConfigurable(IConfig config, List<ICatalogExtensionFactory> catalogExtensionFactories)
    {
        this.config = requireNonNull(config, "config");
        this.catalogExtensionFactories = requireNonNull(catalogExtensionFactories, "catalogExtensionFactories");
        loadSettings();
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            // CSOFF
            JPanel panel = new JPanel(new BorderLayout());
            // CSON
            JLabel label = new JLabel("<html><h2>Catalogs</h2><hr></html>");
            label.setHorizontalAlignment(JLabel.CENTER);

            JTable catalogs = new JTable();
            DefaultTableModel model = new DefaultTableModel(new String[] { "Alias", "Catalog", "Enabled" }, 0)
            {
                @Override
                public boolean isCellEditable(int row, int column)
                {
                    return false;
                };
            };
            for (QueryeerCatalog catalog : payloadbuilderConfig.catalogs)
            {
                model.addRow(new Object[] { catalog.alias, catalog.catalogExtension.getTitle(), !catalog.disabled });
            }
            catalogs.setModel(model);

            label.setAlignmentX(0.5f);
            panel.add(label, BorderLayout.NORTH);
            panel.add(new JScrollPane(catalogs), BorderLayout.CENTER);

            component = panel;
        }
        return component;
    }

    @Override
    public String getTitle()
    {
        return "Catalogs";
    }

    @Override
    public String groupName()
    {
        return "Payloadbuilder";
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
    }

    @Override
    public void removeDirtyStateConsumer(Consumer<Boolean> consumer)
    {
    }

    List<QueryeerCatalog> getCatalogs()
    {
        if (payloadbuilderConfig == null)
        {
            return emptyList();
        }
        return payloadbuilderConfig.catalogs;
    }

    private void loadSettings()
    {
        Map<String, Object> catalogsConfig = config.loadExtensionConfig(NAME);
        payloadbuilderConfig = catalogsConfig.isEmpty() ? new PayloadbuilderConfig()
                : MAPPER.convertValue(catalogsConfig, PayloadbuilderConfig.class);

        try
        {
            loadCatalogExtensions();
        }
        catch (IOException e)
        {
            LOGGER.error("Error loading catalog extensions", e);
        }
    }

    @SuppressWarnings("unchecked")
    void loadCatalogExtensions() throws IOException
    {
        Set<String> seenAliases = new HashSet<>();
        for (QueryeerCatalog catalog : payloadbuilderConfig.catalogs)
        {
            if (!seenAliases.add(lowerCase(catalog.getAlias())))
            {
                throw new IllegalArgumentException("Duplicate alias found in config. Alias: " + catalog.getAlias());
            }
        }

        // Load extension according to config
        // Auto add missing extension with the extensions default alias
        Map<String, ICatalogExtensionFactory> extensions = catalogExtensionFactories.stream()
                .sorted(Comparator.comparingInt(ICatalogExtensionFactory::order))
                .collect(toMap(c -> c.getClass()
                        .getName(), Function.identity(), (e1, e2) -> e1, LinkedHashMap::new));

        Set<ICatalogExtensionFactory> processedFactories = new HashSet<>();

        // Loop configured extensions
        boolean configChanged = false;

        for (QueryeerCatalog catalog : payloadbuilderConfig.catalogs)
        {
            ICatalogExtensionFactory factory = extensions.get(catalog.getFactory());
            processedFactories.add(factory);
            // Disable current catalog if no extension found
            if (factory == null)
            {
                configChanged = true;
                catalog.disabled = true;
            }
            else
            {
                catalog.catalogExtension = factory.create(catalog.getAlias());
                if (catalog.isDisabled())
                {
                    configChanged = true;
                }
                // Enable config
                catalog.disabled = false;
            }
        }

        // Append all new extensions not found in config
        for (ICatalogExtensionFactory factory : extensions.values())
        {
            if (processedFactories.contains(factory))
            {
                continue;
            }

            QueryeerCatalog catalog = new QueryeerCatalog();
            payloadbuilderConfig.catalogs.add(catalog);

            catalog.factory = factory.getClass()
                    .getName();
            catalog.disabled = false;

            String alias = factory.getDefaultAlias();

            // Find an empty alias
            int count = 1;
            String currentAlias = alias;
            while (seenAliases.contains(lowerCase(currentAlias)))
            {
                currentAlias = (alias + count++);
            }

            catalog.alias = alias;
            catalog.catalogExtension = factory.create(alias);

            configChanged = true;
        }

        if (configChanged)
        {
            config.saveExtensionConfig(NAME, MAPPER.convertValue(payloadbuilderConfig, Map.class));
        }
    }

    static class PayloadbuilderConfig
    {
        @JsonProperty
        List<QueryeerCatalog> catalogs = new ArrayList<>();
    }

    /** Catalog extension */
    static class QueryeerCatalog
    {
        @JsonProperty
        private String alias;
        @JsonProperty
        private String factory;
        @JsonProperty
        private boolean disabled;

        @JsonIgnore
        private ICatalogExtension catalogExtension;

        String getAlias()
        {
            return alias;
        }

        String getFactory()
        {
            return factory;
        }

        boolean isDisabled()
        {
            return disabled;
        }

        ICatalogExtension getCatalogExtension()
        {
            return catalogExtension;
        }
    }
}
