package com.queryeer;

import static com.queryeer.QueryeerController.MAPPER;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.service.IConfig;

/** Queryeer config */
class Config implements IConfig
{
    private static final String CONFIG = "queryeer.cfg";
    private static final String CONFIG_DEFAULT = CONFIG + ".default";

    private static final int MAX_RECENT_FILES = 10;
    // @JsonProperty
    // @JsonDeserialize(
    // contentAs = Catalog.class)
    // private List<ICatalogModel> catalogs = new ArrayList<>();
    @JsonProperty
    private String lastOpenPath;
    @JsonProperty
    private final List<String> recentFiles = new ArrayList<>();

    @JsonProperty
    private String defaultQueryProvider;

    private File etcFolder;

    Config() throws IOException
    {
        String etcProp = System.getProperty("etc");

        if (isBlank(etcProp))
        {
            throw new IllegalArgumentException("No etc folder property defined");
        }

        this.etcFolder = new File(etcProp);

        File file = new File(etcFolder, CONFIG);
        if (!file.exists())
        {
            file = new File(etcFolder, CONFIG_DEFAULT);
        }
        else
        {
            // Read existing config
            MAPPER.readerForUpdating(this)
                    .readValue(file);
        }
    }

    // List<ICatalogModel> getCatalogs()
    // {
    // if (catalogs == null)
    // {
    // return emptyList();
    // }
    // return unmodifiableList(catalogs);
    // }

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

    void appendRecentFile(String file)
    {
        recentFiles.remove(file);
        recentFiles.add(0, file);
        if (recentFiles.size() > MAX_RECENT_FILES)
        {
            recentFiles.remove(recentFiles.size() - 1);
        }
    }

    String getDefaultQueryProvider()
    {
        return defaultQueryProvider;
    }

    void setDefaultQueryProvider(String defaultQueryProvider)
    {
        this.defaultQueryProvider = defaultQueryProvider;
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

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadExtensionConfig(String name)
    {
        // First try regular config
        File configFile = new File(etcFolder, FilenameUtils.getName(name + ".cfg"));
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
        File configFile = new File(etcFolder, FilenameUtils.getName(name + ".cfg"));
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

    // /** Load config with provide etc-folder */
    // void loadCatalogExtensions(List<ICatalogExtensionFactory> catalogExtensionFactories) throws IOException
    // {
    // Set<String> seenAliases = new HashSet<>();
    // for (ICatalogModel catalog : catalogs)
    // {
    // if (!seenAliases.add(lowerCase(catalog.getAlias())))
    // {
    // throw new IllegalArgumentException("Duplicate alias found in config. Alias: " + catalog.getAlias());
    // }
    // }
    //
    // // Load extension according to config
    // // Auto add missing extension with the extensions default alias
    // Map<String, ICatalogExtensionFactory> extensions = catalogExtensionFactories.stream()
    // .sorted(Comparator.comparingInt(ICatalogExtensionFactory::order))
    // .collect(toMap(c -> c.getClass()
    // .getName(), Function.identity(), (e1, e2) -> e1, LinkedHashMap::new));
    //
    // Set<ICatalogExtensionFactory> processedFactories = new HashSet<>();
    //
    // // Loop configured extensions
    // boolean configChanged = false;
    //
    // for (ICatalogModel catalog : catalogs)
    // {
    // ICatalogExtensionFactory factory = extensions.get(((Catalog) catalog).getFactory());
    // processedFactories.add(factory);
    // // Disable current catalog if no extension found
    // if (factory == null)
    // {
    // configChanged = true;
    // ((Catalog) catalog).disabled = true;
    // }
    // else
    // {
    // ((Catalog) catalog).catalogExtension = factory.create(catalog.getAlias());
    // if (catalog.isDisabled())
    // {
    // configChanged = true;
    // }
    // // Enable config
    // ((Catalog) catalog).disabled = false;
    // }
    // }
    //
    // // Append all new extensions not found in config
    // for (ICatalogExtensionFactory factory : extensions.values())
    // {
    // if (processedFactories.contains(factory))
    // {
    // continue;
    // }
    //
    // Config.Catalog catalog = new Catalog();
    // catalogs.add(catalog);
    //
    // catalog.factory = factory.getClass()
    // .getName();
    // catalog.disabled = false;
    //
    // String alias = factory.getDefaultAlias();
    //
    // // Find an empty alias
    // int count = 1;
    // String currentAlias = alias;
    // while (seenAliases.contains(lowerCase(currentAlias)))
    // {
    // currentAlias = (alias + count++);
    // }
    //
    // catalog.alias = alias;
    // catalog.catalogExtension = factory.create(alias);
    //
    // configChanged = true;
    // }
    //
    // if (configChanged)
    // {
    // save();
    // }
    // }
    //
    // /** Catalog extension */
    // static class Catalog implements ICatalogModel
    // {
    // @JsonProperty
    // private String alias;
    // @JsonProperty
    // private String factory;
    // @JsonProperty
    // private boolean disabled;
    //
    // @JsonIgnore
    // private ICatalogExtension catalogExtension;
    //
    // @Override
    // public String getAlias()
    // {
    // return alias;
    // }
    //
    // public String getFactory()
    // {
    // return factory;
    // }
    //
    // @Override
    // public boolean isDisabled()
    // {
    // return disabled;
    // }
    //
    // @Override
    // public ICatalogExtension getCatalogExtension()
    // {
    // return catalogExtension;
    // }
    // }
}
