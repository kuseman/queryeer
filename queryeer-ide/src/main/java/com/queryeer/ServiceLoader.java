package com.queryeer;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.PluginHandler.Plugin;
import com.queryeer.api.extensions.IExtension;
import com.queryeer.api.extensions.Inject;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

/** Service loader */
class ServiceLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceLoader.class);
    private final Map<Class<?>, ServiceRegistration> services = new HashMap<>();
    private final Map<Class<?>, Object> resolvedInstances = new HashMap<>();

    ServiceLoader()
    {
    }

    <T> T get(Class<T> clazz)
    {
        ServiceRegistration registration = services.get(clazz);
        if (registration == null)
        {
            return null;
        }
        return clazz.cast(registration.resolve()
                .get(0));
    }

    <T> List<T> getAll(Class<T> clazz)
    {
        ServiceRegistration registration = services.get(clazz);
        if (registration == null)
        {
            return null;
        }
        return registration.resolve();
    }

    void register(Class<?> serviceClass, List<Class<?>> interfaces)
    {
        ServiceRegistration serviceRegistration = services.computeIfAbsent(serviceClass, k -> new ServiceRegistration());
        serviceRegistration.addServiceType(serviceClass);

        if (interfaces != null)
        {
            for (Class<?> iface : interfaces)
            {
                services.computeIfAbsent(iface, i -> serviceRegistration)
                        .addServiceType(serviceClass);
            }
        }
    }

    void register(Class<?> serviceClass, final Object service)
    {
        ServiceRegistration serviceRegistration = services.computeIfAbsent(serviceClass, k -> new ServiceRegistration());
        serviceRegistration.addService(service);
    }

    void register(Class<?> serviceClass)
    {
        register(serviceClass, emptyList());
    }

    void register(Object service)
    {
        requireNonNull(service, "service");
        register(service.getClass(), service);
    }

    void injectExtensions(PluginHandler pluginHandler, File cacheFile) throws InstantiationException, IllegalAccessException
    {
        Collection<Plugin> plugins = pluginHandler.getPlugins();
        ClassLoader[] classLoaders = new ClassLoader[1 + plugins.size()];
        classLoaders[0] = PluginHandler.class.getClassLoader();
        int index = 1;
        for (Plugin plugin : plugins)
        {
            classLoaders[index++] = plugin.getPluginClassLoader();
        }

        List<String> classNames = loadOrScanClassNames(classLoaders, cacheFile);
        for (String className : classNames)
        {
            Class<?> clz = loadClassFromLoaders(className, classLoaders);
            if (clz == null)
            {
                LOGGER.warn("Could not load class from ClassGraph cache, will rescan: {}", className);
                // Cache is stale (class removed/renamed) — delete it and fall through to full scan
                if (cacheFile != null)
                {
                    cacheFile.delete();
                }
                injectExtensions(pluginHandler, cacheFile);
                return;
            }
            List<Class<?>> interfaces = collectIExtensionInterfaces(clz);
            register(clz, interfaces);
        }
    }

    // -------------------------------------------------------------------------
    // ClassGraph scan with file-based cache
    // -------------------------------------------------------------------------

    /**
     * Returns class names to inject: from cache if the classpath fingerprint matches, otherwise from a full ClassGraph scan.
     */
    private List<String> loadOrScanClassNames(ClassLoader[] classLoaders, File cacheFile)
    {
        if (cacheFile != null)
        {
            String fingerprint = computeFingerprint(classLoaders);
            List<String> cached = tryLoadCache(cacheFile, fingerprint);
            if (cached != null)
            {
                LOGGER.debug("ClassGraph scan cache hit ({} classes)", cached.size());
                return cached;
            }
            List<String> classNames = scanClassNames(classLoaders);
            saveCache(cacheFile, fingerprint, classNames);
            return classNames;
        }
        return scanClassNames(classLoaders);
    }

    private static List<String> scanClassNames(ClassLoader[] classLoaders)
    {
        List<String> classNames = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph().enableClassInfo()
                .enableAnnotationInfo()
                .ignoreClassVisibility()
                .overrideClassLoaders(classLoaders)
                .scan())
        {
            ClassInfoList list = scanResult.getClassesImplementing(IExtension.class)
                    .filter(c -> !c.isInterface());
            for (ClassInfo l : list)
            {
                classNames.add(l.getName());
            }

            list = scanResult.getClassesWithAnnotation(Inject.class);
            for (ClassInfo l : list)
            {
                String name = l.getName();
                if (!classNames.contains(name))
                {
                    classNames.add(name);
                }
            }
        }
        return classNames;
    }

    /**
     * Fingerprint based on the system classpath string and the last-modified time of each JAR/directory exposed by the URLClassLoader hierarchy of the given loaders. For directories (e.g.
     * {@code target/classes} in dev mode) the newest {@code .class} file's lastModified is used so any recompile invalidates the cache.
     */
    private static String computeFingerprint(ClassLoader[] classLoaders)
    {
        String classPath = System.getProperty("java.class.path", "");
        long h = classPath.hashCode();

        // In Java 9+ the application classloader is not a URLClassLoader, so we must
        // walk java.class.path entries explicitly to pick up modification times of
        // directories like target/classes (recompiling adds files but doesn't change
        // the classpath string, which would leave the fingerprint stale).
        for (String entry : classPath.split(File.pathSeparator))
        {
            if (!entry.isEmpty())
            {
                h = h * 31 + Long.hashCode(newestModified(new File(entry)));
            }
        }

        // Plugin classloaders are URLClassLoaders — handle them the same way.
        for (ClassLoader cl : classLoaders)
        {
            if (cl instanceof URLClassLoader ucl)
            {
                for (URL url : ucl.getURLs())
                {
                    h = h * 31 + url.toString()
                            .hashCode();
                    try
                    {
                        File f = Path.of(url.toURI())
                                .toFile();
                        h = h * 31 + Long.hashCode(newestModified(f));
                    }
                    catch (Exception e)
                    {
                        // Not a local file URL — skip
                    }
                }
            }
        }
        return Long.toHexString(h);
    }

    /** Returns {@code f.lastModified()} for files; for directories returns the max lastModified of all {@code .class} files inside (recursive). */
    private static long newestModified(File f)
    {
        if (!f.isDirectory())
        {
            return f.lastModified();
        }
        long max = f.lastModified();
        File[] children = f.listFiles();
        if (children != null)
        {
            for (File child : children)
            {
                long t = newestModified(child);
                if (t > max)
                {
                    max = t;
                }
            }
        }
        return max;
    }

    private static List<String> tryLoadCache(File cacheFile, String fingerprint)
    {
        if (!cacheFile.exists())
        {
            return null;
        }
        try
        {
            List<String> lines = FileUtils.readLines(cacheFile, StandardCharsets.UTF_8);
            if (!lines.isEmpty()
                    && fingerprint.equals(lines.get(0)))
            {
                return new ArrayList<>(lines.subList(1, lines.size()));
            }
        }
        catch (IOException e)
        {
            LOGGER.warn("Error reading ClassGraph cache: {}", cacheFile, e);
        }
        return null;
    }

    private static void saveCache(File cacheFile, String fingerprint, List<String> classNames)
    {
        List<String> lines = new ArrayList<>(classNames.size() + 1);
        lines.add(fingerprint);
        lines.addAll(classNames);
        try
        {
            FileUtils.writeLines(cacheFile, StandardCharsets.UTF_8.name(), lines);
        }
        catch (IOException e)
        {
            LOGGER.warn("Error writing ClassGraph cache: {}", cacheFile, e);
        }
    }

    private static Class<?> loadClassFromLoaders(String className, ClassLoader[] classLoaders)
    {
        for (ClassLoader cl : classLoaders)
        {
            try
            {
                return cl.loadClass(className);
            }
            catch (ClassNotFoundException e)
            {
                // try next classloader
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------

    /**
     * Collect all IExtension-assignable interfaces from the full class/interface hierarchy using BFS. This ensures that e.g. a class implementing IAIAssistantProvider (which extends IConfigurable)
     * also gets registered under IConfigurable.
     */
    private static List<Class<?>> collectIExtensionInterfaces(Class<?> clz)
    {
        List<Class<?>> result = new ArrayList<>();
        Set<Class<?>> visited = new LinkedHashSet<>();
        Queue<Class<?>> queue = new ArrayDeque<>();
        queue.add(clz);
        while (!queue.isEmpty())
        {
            Class<?> current = queue.poll();
            if (current == null
                    || !visited.add(current))
            {
                continue;
            }
            for (Class<?> iface : current.getInterfaces())
            {
                if (IExtension.class.isAssignableFrom(iface)
                        && iface != IExtension.class
                        && !result.contains(iface))
                {
                    result.add(iface);
                }
                queue.add(iface);
            }
            if (current.getSuperclass() != null)
            {
                queue.add(current.getSuperclass());
            }
        }
        return result;
    }

    /** Concrete type of service */
    private class ServiceType
    {
        private final Class<?> type;
        private final List<Type> dependenceis;
        private final Constructor<?> contructor;

        private Object instance;

        ServiceType(Object instance)
        {
            this.type = null;
            this.dependenceis = null;
            this.contructor = null;
            this.instance = instance;

            resolvedInstances.put(instance.getClass(), instance);
        }

        ServiceType(Class<?> type)
        {
            this.type = type;
            Pair<Constructor<?>, List<Type>> pair = getDependencies(type);
            this.contructor = pair.getKey();
            this.dependenceis = pair.getValue();
        }

        Object resolve()
        {
            if (instance == null)
            {
                // First check if this type is already resolved
                instance = resolvedInstances.get(type);
                if (instance != null)
                {
                    return instance;
                }

                Object[] args = new Object[dependenceis.size()];
                for (int i = 0; i < dependenceis.size(); i++)
                {
                    Type dep = dependenceis.get(i);

                    Object arg = null;
                    if (dep instanceof Class<?>)
                    {
                        arg = get((Class<?>) dep);
                    }
                    else if (dep instanceof ParameterizedType)
                    {
                        ParameterizedType ptype = (ParameterizedType) dep;
                        if (List.class.isAssignableFrom((Class<?>) ptype.getRawType()))
                        {
                            arg = getAll((Class<?>) ptype.getActualTypeArguments()[0]);
                            // A List<T> dependency with no registered implementations resolves to an empty list
                            if (arg == null)
                            {
                                arg = emptyList();
                            }
                        }
                    }
                    if (arg == null)
                    {
                        throw new IllegalArgumentException("Error resolving dependency " + dependenceis.get(i) + " for type " + type);
                    }
                    args[i] = arg;
                }

                try
                {
                    contructor.setAccessible(true);
                    instance = contructor.newInstance(args);
                    resolvedInstances.put(type, instance);
                }
                catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
                {
                    throw new IllegalArgumentException("Error creating type " + type, e);
                }
            }

            return instance;
        }

        private Pair<Constructor<?>, List<Type>> getDependencies(Class<?> type)
        {
            Constructor<?>[] ctors = type.getDeclaredConstructors();
            Constructor<?> ctor;
            if (ctors.length > 1)
            {
                ctor = Arrays.stream(ctors)
                        .filter(c -> c.getAnnotation(Inject.class) != null)
                        .findAny()
                        .orElse(null);

                if (ctor == null)
                {
                    throw new RuntimeException("Extension classes must have one contructor or a constructor annotated with: " + Inject.class + ". Class: " + type + " have " + ctors.length);
                }
            }
            else
            {
                ctor = ctors[0];
            }

            return Pair.of(ctor, asList(ctor.getGenericParameterTypes()));
        }
    }

    private class ServiceRegistration
    {
        private final List<ServiceType> serviceTypes = new ArrayList<>();
        private List<Object> instances;

        @SuppressWarnings("unchecked")
        <T> List<T> resolve()
        {
            if (instances == null)
            {
                instances = unmodifiableList(serviceTypes.stream()
                        .map(ServiceType::resolve)
                        .collect(toList()));
            }

            return (List<T>) instances;
        }

        void addService(Object service)
        {
            serviceTypes.add(new ServiceType(service));
        }

        void addServiceType(Class<?> serviceClass)
        {
            // Cannot wire abstract types
            if (Modifier.isAbstract(serviceClass.getModifiers()))
            {
                return;
            }
            for (ServiceType serviceType : serviceTypes)
            {
                if (serviceType.type != null
                        && serviceType.type == serviceClass)
                {
                    // This type is already registered
                    return;
                }
            }
            serviceTypes.add(new ServiceType(serviceClass));
        }
    }
}
