package com.queryeer;

import static com.queryeer.QueryeerController.MAPPER;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IConfig;

import se.kuseman.payloadbuilder.core.CsvOutputWriter.CsvSettings;
import se.kuseman.payloadbuilder.core.JsonOutputWriter.JsonSettings;

/** Queryeer config */
class Config implements IConfig
{
    private static final String CONFIG = "queryeer.cfg";
    private static final String CONFIG_DEFAULT = CONFIG + ".default";

    private static final int MAX_RECENT_FILES = 10;
    @JsonProperty
    private List<Catalog> catalogs = new ArrayList<>();
    @JsonProperty
    private String lastOpenPath;
    @JsonProperty
    private final List<String> recentFiles = new ArrayList<>();
    // @JsonProperty
    // private final OutputConfig outputConfig = new OutputConfig();
    private File etcFolder;

    Config(File etcFolder) throws IOException
    {
        this.etcFolder = etcFolder;

        File file = new File(etcFolder, CONFIG);
        if (!file.exists())
        {
            file = new File(etcFolder, CONFIG_DEFAULT);
        }

        // Read existing config
        if (file.exists())
        {
            MAPPER.readerForUpdating(this)
                    .readValue(file);
        }
    }

    List<Catalog> getCatalogs()
    {
        if (catalogs == null)
        {
            return emptyList();
        }
        return catalogs;
    }

    String getLastOpenPath()
    {
        return lastOpenPath;
    }

    void setLastOpenPath(String lastOpenPath)
    {
        this.lastOpenPath = lastOpenPath;
    }

    List<String> getRecentFiles()
    {
        return recentFiles;
    }

    // public OutputConfig getOutputConfig()
    // {
    // return outputConfig;
    // }

    void appendRecentFile(String file)
    {
        recentFiles.remove(file);
        recentFiles.add(0, file);
        if (recentFiles.size() > MAX_RECENT_FILES)
        {
            recentFiles.remove(recentFiles.size() - 1);
        }
    }

    /** Save config to disk */
    void save()
    {
        File file = new File(etcFolder, CONFIG);
        if (!file.exists())
        {
            try
            {
                file.createNewFile();
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error creating config file: " + file, e);
            }
        }
        try
        {
            QueryeerController.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(file, this);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error saving config to " + file.getAbsolutePath(), e);
        }
    }

    // @Override
    // public Map<String, Object> loadExtensionConfig(String name)
    // {
    // return loadExtensionConfig(name);
    // }
    //
    // public void saveOutputExtensionConfig(IOutputExtension extension, Map<String, Object> config)
    // {
    // saveExtensionConfig(extension.getName(), config);
    // }
    //
    // /** Load extension config for provided extension class */
    // Map<String, Object> loadCatalogExtensionConfig(ICatalogExtension extension)
    // {
    // return loadExtensionConfig(extension.getName());
    // }
    //
    // /** Save provided catalog extension config */
    // void saveCatalogExtensionConfig(ICatalogExtension extension, Map<String, Object> config)
    // {
    // saveExtensionConfig(extension.getName(), config);
    // }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadExtensionConfig(String name)
    {
        // First try regular config
        File configFile = new File(etcFolder, name + ".cfg");
        if (!configFile.exists())
        {
            return new HashMap<>();
        }

        // Don't try to load empty config files
        if (FileUtils.sizeOf(configFile) == 0)
        {
            return new HashMap<>();
        }

        try
        {
            return QueryeerController.MAPPER.readValue(configFile, Map.class);
        }
        catch (IOException e)
        {
            System.err.println("Error reading extension config: " + configFile + ", exception:" + e);
            return new HashMap<>();
        }
    }

    @Override
    public void saveExtensionConfig(String name, Map<String, Object> config)
    {
        File configFile = new File(etcFolder, name + ".cfg");
        try
        {
            QueryeerController.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(configFile, config);
        }
        catch (IOException e)
        {
            System.err.println("Error writing plugin config: " + configFile + ", exception:" + e);
        }
    }

    /** Load config with provide etc-folder */
    void loadCatalogExtensions(ServiceLoader serviceLoader) throws IOException
    {
        // File file = new File(etcFolder, CONFIG);
        // if (!file.exists())
        // {
        // file = new File(etcFolder, CONFIG_DEFAULT);
        // }

        // try
        // {
        // if (file.exists())
        // {
        // config = MAPPER.readerForUpdating(config)
        // .readValue(file);
        // }

        Set<String> seenAliases = new HashSet<>();
        for (Config.Catalog catalog : catalogs)
        {
            if (!seenAliases.add(lowerCase(catalog.getAlias())))
            {
                throw new IllegalArgumentException("Duplicate alias found in config. Alias: " + catalog.getAlias());
            }
        }

        serviceLoader.getAll(ICatalogExtensionFactory.class);

        // Load extension according to config
        // Auto add missing extension with the extensions default alias
        Map<String, ICatalogExtensionFactory> extensions = serviceLoader.getAll(ICatalogExtensionFactory.class)
                .stream()
                .sorted((a, b) -> Integer.compare(a.order(), b.order()))
                .collect(toMap(c -> c.getClass()
                        .getName(), Function.identity(), (e1, e2) -> e1, LinkedHashMap::new));

        Set<ICatalogExtensionFactory> processedFactories = new HashSet<>();

        // Loop configured extensions
        boolean configChanged = false;

        for (Config.Catalog catalog : catalogs)
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
                catalog.catalogExtension = factory.create(catalog.alias);
                if (catalog.disabled)
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

            Config.Catalog catalog = new Catalog();
            catalogs.add(catalog);

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
            save();
        }
        // }
        // catch (Exception e)
        // {
        // throw new RuntimeException("Error loading config from " + file.getAbsolutePath(), e);
        // }
    }

    /** Catalog extension */
    static class Catalog
    {
        @JsonProperty
        private String alias;
        @JsonProperty
        private String factory;
        @JsonProperty
        private boolean disabled;

        @JsonIgnore
        private ICatalogExtension catalogExtension;

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

    /** Output configuration */
    static class OutputConfig
    {
        @JsonProperty
        private final CsvSettings csvSettings = new CsvSettings();
        @JsonProperty
        private final JsonSettings jsonSettings = new JsonSettings();

        public CsvSettings getCsvSettings()
        {
            return csvSettings;
        }

        public JsonSettings getJsonSettings()
        {
            return jsonSettings;
        }
    }
}
