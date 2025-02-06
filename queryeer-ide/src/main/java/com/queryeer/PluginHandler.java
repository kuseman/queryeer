package com.queryeer;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class handling plugins
 * 
 * <pre>
 *  - System property 'plugins' pointing to a parent plugins folder.
 *  
 *    plugins
 *      pluginA
 *      pluginB
 *    
 *    Used in distribution mode
 *  
 *  - One or more system properties named 'pluginX' where X is 1 to 10 pointing to a plugin folder 
 *    (pluginA or pluginB above) 
 * 
 *  A shared libraries folder can be specified with system property 'shared'. If exists
 *  a shared class loaded will be created that all plugin class loaders will get as parent.
 *  Use full for JDBC-drivers etc.
 * 
 * </pre>
 */
class PluginHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginHandler.class);
    private final Map<String, Plugin> plugins = new LinkedHashMap<>();
    private final Config config;

    PluginHandler(Config config)
    {
        this.config = config;
        load();
    }

    private File[] getPluginDirs()
    {
        String pluginsProp = System.getProperty("plugins");
        if (!isBlank(pluginsProp))
        {
            File pluginsDir = new File(pluginsProp);
            if (!pluginsDir.exists())
            {
                throw new IllegalArgumentException("Plugins directory: " + pluginsDir + " does not exists");
            }
            File[] result = pluginsDir.listFiles(f -> f.isDirectory()
                    || StringUtils.endsWithIgnoreCase(f.getName(), ".zip"));
            // Sort folders to get the newest version first in case of double versions
            Arrays.sort(result, (a, b) -> Utils.compareVersions(a.getName(), b.getName()));
            return result;
        }

        // Try to find specific plugin dirs from system properties
        List<File> pluginDirs = new ArrayList<>();

        for (int i = 1; i < 11; i++)
        {
            String property = System.getProperty("plugin" + i);
            if (!isBlank(property))
            {
                File pluginDir = new File(property);
                if (!pluginDir.exists())
                {
                    throw new IllegalArgumentException("Plugin path: " + pluginDir + " does not exists");
                }
                pluginDirs.add(pluginDir);
            }
        }

        return pluginDirs.toArray(new File[0]);
    }

    private void load()
    {
        URLClassLoader sharedClassLoader = createSharedClassLoader();

        File[] pluginDirs = getPluginDirs();
        for (File pluginDir : pluginDirs)
        {
            String pluginName = pluginDir.getName();
            String lowerPluginName = lowerCase(pluginName);
            if (plugins.containsKey(lowerPluginName))
            {
                LOGGER.error("Plugin {}, already loaded ", pluginName);
                continue;
            }

            try
            {
                ClassLoader cl = createPluginClassLoader(sharedClassLoader, pluginDir);
                if (cl != null)
                {
                    plugins.put(pluginName, new Plugin(cl));
                }
            }
            catch (Throwable t)
            {
                LOGGER.warn("Error loading plugin with path: {}", pluginDir, t);
            }
        }
    }

    Collection<Plugin> getPlugins()
    {
        return plugins.values();
    }

    private ClassLoader createPluginClassLoader(URLClassLoader sharedClassLoader, File dir) throws IOException
    {
        final ClassLoader coreClassLoader = getClass().getClassLoader();
        ClassLoader parent = sharedClassLoader;
        if (parent == null)
        {
            parent = coreClassLoader.getParent();
        }

        // A zipped plugin distribution
        if (StringUtils.endsWithIgnoreCase(dir.getAbsolutePath(), "zip"))
        {
            return new ZIPClassLoader(dir.getAbsolutePath(), coreClassLoader, parent);
        }
        // A folder with the plugin jars
        URL[] urls = Arrays.stream(Optional.of(dir.listFiles())
                .orElse(new File[0]))
                .filter(f -> f.getName()
                        .endsWith(".jar"))
                .sorted()
                .map(File::toURI)
                .map(this::toUrl)
                .toArray(URL[]::new);

        return new URLClassLoader(urls, parent)
        {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
            {
                // has the class loaded already?
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null)
                {
                    // API classes should be loaded by current loader
                    if (isCoreClass(name))
                    {
                        loadedClass = coreClassLoader.loadClass(name);
                    }
                    else
                    {
                        loadedClass = super.loadClass(name, resolve);
                    }
                }

                if (resolve)
                {
                    // marked to resolve
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        };
    }

    private static boolean isCoreClass(String name)
    {
        return name.startsWith("se.kuseman.payloadbuilder.api")
                || name.startsWith("com.queryeer.api")
                || name.startsWith("org.slf4j");
    }

    private URLClassLoader createSharedClassLoader()
    {
        File sharedDir = config.getSharedFolder();
        if (sharedDir == null)
        {
            return null;
        }

        if (!sharedDir.exists())
        {
            throw new IllegalArgumentException("Shared directory: " + sharedDir + " does not exists");
        }

        final URL[] urls = Arrays.stream(Optional.of(sharedDir.listFiles())
                .orElse(new File[0]))
                .filter(f -> f.getName()
                        .endsWith(".jar"))
                .sorted()
                .map(File::toURI)
                .map(this::toUrl)
                .toArray(URL[]::new);

        return new URLClassLoader(urls, getClass().getClassLoader()
                .getParent());
    }

    private URL toUrl(final URI uri)
    {
        try
        {
            return uri.toURL();
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException(e);
        }
    }

    static class Plugin
    {
        private final ClassLoader pluginClassLoader;

        Plugin(ClassLoader pluginClassLoader)
        {
            this.pluginClassLoader = pluginClassLoader;
        }

        ClassLoader getPluginClassLoader()
        {
            return pluginClassLoader;
        }
    }

    /** Class loader used when having a ZIP file plugin */
    static class ZIPClassLoader extends URLClassLoader
    {
        private final String zipFile;
        private final ClassLoader coreClassLoader;
        // Not ideal here to have open filesystems the isn't closed but it works
        private final List<FileSystem> jarFileSystems;

        ZIPClassLoader(String zipFile, ClassLoader coreClassLoader, ClassLoader parent) throws ZipException, IOException
        {
            super(getJarUrls(zipFile), parent);
            this.zipFile = zipFile;
            this.coreClassLoader = coreClassLoader;
            this.jarFileSystems = createJarFileSystems(zipFile, getURLs());
        }

        @Override
        public InputStream getResourceAsStream(String name)
        {
            for (FileSystem fs : jarFileSystems)
            {
                Path path = fs.getPath(name);
                if (Files.exists(path))
                {
                    try
                    {
                        return Files.newInputStream(path);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException("Error loading resource: " + name, e);
                    }
                }
            }

            return super.getResourceAsStream(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
        {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null)
            {
                if (isCoreClass(name))
                {
                    loadedClass = coreClassLoader.loadClass(name);
                }
                else
                {
                    String classPath = "/" + name.replace('.', '/') + ".class";
                    try
                    {
                        for (FileSystem fs : jarFileSystems)
                        {
                            Path path = fs.getPath(classPath);
                            if (Files.exists(path))
                            {
                                byte[] bytes = Files.readAllBytes(path);
                                loadedClass = defineClass(name, bytes, 0, bytes.length);
                            }

                            if (loadedClass != null)
                            {
                                break;
                            }
                        }
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException("Error loading class: " + name + " from ZIPPlugin: " + zipFile, e);
                    }

                    // Load from parent load if not found in zips
                    if (loadedClass == null)
                    {
                        loadedClass = super.loadClass(name, resolve);
                    }
                }
            }

            if (resolve)
            {
                // marked to resolve
                resolveClass(loadedClass);
            }
            return loadedClass;
        }

        private static List<FileSystem> createJarFileSystems(String zipFile, URL[] jarUrls)
        {
            try (FileSystem zipFileSystem = FileSystems.newFileSystem(Path.of(zipFile)))
            {
                List<FileSystem> fileSystems = new ArrayList<>();
                for (URL jarUrl : jarUrls)
                {
                    String jarFile = StringUtils.substringAfterLast(jarUrl.getFile(), "!/");
                    fileSystems.add(FileSystems.newFileSystem(zipFileSystem.getPath(jarFile)));
                }
                return fileSystems;
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error creating jar file systems for ZIPPlugin: " + zipFile, e);
            }
        }

        private static URL[] getJarUrls(String zipFile)
        {
            try (ZipFile zf = new ZipFile(zipFile))
            {
                return zf.stream()
                        .filter(ze -> ze.getName()
                                .endsWith(".jar"))
                        .map(ze -> toUrl(zipFile, ze.getName()))
                        .toArray(URL[]::new);
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error processing plugin zip: " + zipFile, e);
            }
        }

        private static URL toUrl(String zipFile, String jarFile)
        {
            try
            {
                return new URL("jar:file://" + zipFile + "!/" + jarFile);
            }
            catch (MalformedURLException e)
            {
                throw new RuntimeException("Error creating URL to nested jar file", e);
            }
        }

    }
}
