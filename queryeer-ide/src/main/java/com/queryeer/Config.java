package com.queryeer;

import static com.queryeer.QueryeerController.MAPPER;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.service.IConfig;

/** Queryeer config */
class Config implements IConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);
    private static final String CONFIG = "queryeer.cfg";
    private static final String SESSION = "queryeer.session.cfg";

    private static final int MAX_RECENT_FILES = 10;
    private final File etcFolder;
    private final File sessionFile;

    @JsonProperty
    private String lastOpenPath;
    @JsonProperty
    private final List<String> recentFiles = new ArrayList<>();

    /** Class name of the default query engine for new files */
    @JsonProperty
    private String defaultQueryEngineClassName;

    @JsonIgnore
    private IQueryEngine defaultQueryEngine;

    /** Associations between file extension and query engine */
    @JsonProperty
    private List<QueryEngineAssociation> queryEngineAssociations = new ArrayList<>();

    @JsonIgnore
    private List<IQueryEngine> engines = emptyList();

    private QueryeerSession session;

    Config(File etcFolder) throws IOException
    {
        this.etcFolder = etcFolder;
        File file = new File(etcFolder, CONFIG);
        if (!file.exists())
        {
            JOptionPane.showMessageDialog(null, """
                    Config folder: '%s' doesn't exist, creating.
                    NOTE! If this was an upgrade you can copy the config
                    from the previous distribution. Config is now defaulted to your home
                    folder to make upgrades easier.
                    This behaviour can be changed by altering the startup script by passing an explicit 'etc'
                    JVM argument.
                    """.formatted(etcFolder.getAbsolutePath()), "Config folder", JOptionPane.INFORMATION_MESSAGE);

            etcFolder.mkdirs();
            file.createNewFile();
        }
        else
        {
            // Read existing config
            MAPPER.readerForUpdating(this)
                    .readValue(file);
        }

        sessionFile = new File(etcFolder, SESSION);
        if (sessionFile.exists())
        {
            try
            {
                session = MAPPER.readValue(sessionFile, QueryeerSession.class);
            }
            catch (IOException e)
            {
                LOGGER.error("Error loading session config", e);
            }
        }
        if (session == null)
        {
            session = new QueryeerSession();
        }
    }

    List<IQueryEngine> getEngines()
    {
        return engines;
    }

    File getEtcFolder()
    {
        return etcFolder;
    }

    /** Init config with discovered engines */
    void init(List<IQueryEngine> engines)
    {
        this.engines = unmodifiableList(engines);

        boolean save = false;

        for (IQueryEngine engine : engines)
        {
            String engineClass = engine.getClass()
                    .getSimpleName();

            if (engineClass.equalsIgnoreCase(defaultQueryEngineClassName))
            {
                defaultQueryEngine = engine;
            }

            boolean found = false;
            for (QueryEngineAssociation ass : queryEngineAssociations)
            {
                if (engineClass.equalsIgnoreCase(ass.getQueryEngineClassName()))
                {
                    found = true;
                    ass.engine = engine;
                    break;
                }
            }

            // No association found, create one
            if (!found)
            {
                QueryEngineAssociation ass = new QueryEngineAssociation();
                ass.queryEngineClassName = engineClass;
                // Add new ones as disabled
                ass.engine = engine;
                ass.extension = engine.getDefaultFileExtension();
                queryEngineAssociations.add(ass);
                save = true;
            }
        }

        if (defaultQueryEngine == null)
        {
            defaultQueryEngine = engines.get(0);
            save = true;
        }

        if (save)
        {
            save();
        }
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

    void appendRecentFile(String file)
    {
        recentFiles.remove(file);
        recentFiles.add(0, file);
        if (recentFiles.size() > MAX_RECENT_FILES)
        {
            recentFiles.remove(recentFiles.size() - 1);
        }
    }

    void removeRecentFile(String file)
    {
        recentFiles.remove(file);
    }

    IQueryEngine getDefaultQueryEngine()
    {
        return defaultQueryEngine;
    }

    void setDefaultQueryEngine(IQueryEngine defaultQueryEngine)
    {
        requireNonNull(defaultQueryEngine);
        this.defaultQueryEngine = defaultQueryEngine;
        this.defaultQueryEngineClassName = defaultQueryEngine.getClass()
                .getSimpleName();
    }

    List<QueryEngineAssociation> getQueryEngineAssociations()
    {
        return queryEngineAssociations;
    }

    IQueryEngine getQueryEngine(String filename)
    {
        String extension = FilenameUtils.getExtension(filename);
        if (isBlank(extension))
        {
            return engines.get(0);
        }

        for (QueryEngineAssociation ass : queryEngineAssociations)
        {
            if (isBlank(ass.extension)
                    || ass.engine == null)
            {
                continue;
            }

            if (StringUtils.equalsAnyIgnoreCase(extension, ass.extension))
            {
                return ass.engine;
            }
        }

        return engines.get(0);
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
            LOGGER.error("Error reading extension config: {}", configFile, e);
            return new HashMap<>();
        }
    }

    @Override
    public void saveExtensionConfig(String name, Map<String, ?> config)
    {
        File configFile = new File(etcFolder, FilenameUtils.getName(name + ".cfg"));
        try
        {
            QueryeerController.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(configFile, config);
        }
        catch (IOException e)
        {
            LOGGER.error("Error writing extension config: {}", configFile, e);
        }
    }

    @Override
    public File getConfigFileName(String name)
    {
        return new File(etcFolder, FilenameUtils.getName(name + ".cfg"));
    }

    @JsonIgnore
    @Override
    public List<IQueryEngine> getQueryEngines()
    {
        return engines;
    }

    QueryeerSession getSession()
    {
        return session;
    }

    void saveSession()
    {
        // Invalid index, set to first
        if (session.activeFileIndex < 0
                || session.activeFileIndex >= session.files.size())
        {
            session.activeFileIndex = 0;
        }

        try
        {
            QueryeerController.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(sessionFile, session);
        }
        catch (IOException e)
        {
            LOGGER.error("Error wrting session config to: {}", sessionFile, e);
        }
    }

    /** Session with opened files */
    static class QueryeerSession
    {
        /** The active file index. */
        @JsonProperty
        int activeFileIndex;
        /** Opened files. */
        @JsonProperty
        List<QueryeerSessionFile> files = new ArrayList<>();
    }

    /** A opened file in the session. */
    static class QueryeerSessionFile
    {
        @JsonProperty
        File file;
        @JsonProperty
        boolean isNew;
        @JsonProperty
        File backupFile;

        @Override
        public int hashCode()
        {
            return file != null ? file.hashCode()
                    : 0;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj == null)
            {
                return false;
            }
            else if (obj == this)
            {
                return true;
            }
            else if (obj instanceof QueryeerSessionFile that)
            {
                return Objects.equals(file, that.file)
                        && isNew == that.isNew
                        && Objects.equals(backupFile, that.backupFile);
            }
            return false;
        }
    }

    static class QueryEngineAssociation
    {
        @JsonProperty
        private String extension;

        @JsonProperty
        private String queryEngineClassName;

        @JsonIgnore
        private IQueryEngine engine;

        String getExtension()
        {
            return extension;
        }

        void setExtension(String extension)
        {
            this.extension = extension;
        }

        String getQueryEngineClassName()
        {
            return queryEngineClassName;
        }

        void setQueryEngineClassName(String queryEngineClassName)
        {
            this.queryEngineClassName = queryEngineClassName;
        }

        IQueryEngine getEngine()
        {
            return engine;
        }
    }
}
