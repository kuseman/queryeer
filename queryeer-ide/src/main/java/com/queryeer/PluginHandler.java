package com.queryeer;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final Map<String, Plugin> plugins = new LinkedHashMap<>();

    PluginHandler()
    {
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
            File[] result = pluginsDir.listFiles(f -> f.isDirectory());
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
                    throw new IllegalArgumentException("Plugin directory: " + pluginDir + " does not exists");
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
                System.err.println("Plugin with: " + pluginName + ", already loaded");
                continue;
            }

            URLClassLoader cl = createPluginClassLoader(sharedClassLoader, pluginDir);
            if (cl != null)
            {
                plugins.put(pluginName, new Plugin(cl));
            }
        }
    }

    Collection<Plugin> getPlugins()
    {
        return plugins.values();
    }

    // /**
    // * Load extension with provided class from plugins
    // *
    // * @param clazz Class to load
    // */
    // <T> List<T> load(Class<T> clazz)
    // {
    // // ClassLoader[] classLoaders = new ClassLoader[1 + plugins.size()];
    // // classLoaders[0] = PluginHandler.class.getClassLoader();
    // // int index = 1;
    // // for (Plugin plugin : plugins.values())
    // // {
    // // classLoaders[index++] = plugin.pluginClassLoader;
    // // }
    // //
    // // long time = System.currentTimeMillis();
    // // try (ScanResult scanResult = new ClassGraph().enableClassInfo()
    // // .overrideClassLoaders(classLoaders)
    // // .scan())
    // // {
    // //
    // // ClassInfoList list = scanResult.getClassesImplementing(IExtensionFactory.class)
    // // .filter(c -> !c.isInterface());
    // // for (ClassInfo l : list)
    // // {
    // // Class<?> clz = l.loadClass();
    // // System.out.println(clz.getName() + " " + clz.getClassLoader());
    // // }
    // // }
    // // System.out.println(System.currentTimeMillis() - time);
    //
    // List<T> services = new ArrayList<>();
    //
    // // Load from IDE class loader
    // addServices(services, ServiceLoader.load(clazz));
    // // Load from each plugin
    // for (Plugin plugin : plugins.values())
    // {
    // addServices(services, ServiceLoader.load(clazz, plugin.pluginClassLoader));
    // }
    // return services;
    // }
    //
    // private <T> void addServices(List<T> services, ServiceLoader<T> serviceLoader)
    // {
    // Iterator<T> iterator = serviceLoader.iterator();
    // while (iterator.hasNext())
    // {
    // T service = iterator.next();
    // services.add(service);
    // }
    // }

    private URLClassLoader createPluginClassLoader(URLClassLoader sharedClassLoader, File dir)
    {
        URL[] urls;

        // A folder pointing to a unziped jar
        if (new File(dir, "META-INF").exists())
        {
            urls = new URL[] { toUrl(dir.toURI()) };
        }
        else
        {
            urls = Arrays.stream(Optional.of(dir.listFiles())
                    .orElse(new File[] {}))
                    .filter(f -> f.getName()
                            .endsWith(".jar"))
                    .sorted()
                    .map(File::toURI)
                    .map(this::toUrl)
                    .toArray(URL[]::new);
        }

        final ClassLoader coreClassLoader = getClass().getClassLoader();

        ClassLoader parent = sharedClassLoader;
        if (parent == null)
        {
            parent = coreClassLoader.getParent();
        }

        return new URLClassLoader(urls, sharedClassLoader)
        {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
            {
                // has the class loaded already?
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null)
                {
                    // API classes should be loaded by current loader
                    if (name.startsWith("se.kuseman.payloadbuilder.api")
                            || name.startsWith("com.queryeer.api"))
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

    private URLClassLoader createSharedClassLoader()
    {
        String sharedProperty = System.getProperty("shared");
        if (sharedProperty == null)
        {
            return null;
        }

        File sharedDir = new File(sharedProperty);
        if (!sharedDir.exists())
        {
            throw new IllegalArgumentException("Shared directory: " + sharedProperty + " does not exists");
        }

        final URL[] urls = Arrays.stream(Optional.of(sharedDir.listFiles())
                .orElse(new File[] {}))
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
}
