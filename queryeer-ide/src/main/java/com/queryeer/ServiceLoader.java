package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;

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
    private final Map<Class<?>, Object> services = new HashMap<>();
    private PluginHandler pluginHandler;

    ServiceLoader()
    {
        this.pluginHandler = new PluginHandler();
    }

    @SuppressWarnings("unchecked")
    <T> T get(Class<T> clazz)
    {
        Object obj = services.get(clazz);
        if (obj instanceof ServiceList)
        {
            return (T) ((ServiceList) obj).get(0);
        }
        return (T) obj;
    }

    @SuppressWarnings("unchecked")
    <T> List<T> getAll(Class<T> clazz)
    {
        Object obj = services.get(clazz);
        if (obj == null)
        {
            return null;
        }
        if (obj instanceof ServiceList)
        {
            return (List<T>) obj;
        }
        return Collections.singletonList((T) obj);
    }

    void register(Class<?> serviceClass, final Object service)
    {
        services.compute(serviceClass, (k, v) ->
        {
            if (v == null)
            {
                return service;
            }

            // Switch to a list of services
            List<Object> services;
            if (v instanceof ServiceList)
            {
                services = (ServiceList) v;
            }
            else
            {
                services = new ServiceList();
                services.add(v);
            }

            /* Skip register multiple of the same instance */
            if (services.stream()
                    .anyMatch(s -> s == service))
            {
                return services;
            }

            services.add(service);
            return services;
        });
    }

    void register(Object service)
    {
        requireNonNull(service, "service");
        register(service.getClass(), service);
    }

    <T> void registerAll(Class<T> serviceClass, List<T> services)
    {
        for (Object service : services)
        {
            register(serviceClass, service);
        }
    }

    void injectExtensions() throws InstantiationException, IllegalAccessException
    {
        Collection<Plugin> plugins = pluginHandler.getPlugins();
        ClassLoader[] classLoaders = new ClassLoader[1 + plugins.size()];
        classLoaders[0] = PluginHandler.class.getClassLoader();
        int index = 1;
        for (Plugin plugin : plugins)
        {
            classLoaders[index++] = plugin.getPluginClassLoader();
        }

        List<Class<?>> extensionClasses = new ArrayList<>();
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
                Class<?> clz = l.loadClass();
                extensionClasses.add(clz);
            }

            list = scanResult.getClassesWithAnnotation(Inject.class);
            for (ClassInfo l : list)
            {
                Class<?> clz = l.loadClass();
                extensionClasses.add(clz);
            }
        }

        // Start wiring
        while (true)
        {
            int count = extensionClasses.size();
            Iterator<Class<?>> it = extensionClasses.iterator();
            while (it.hasNext())
            {
                Class<?> clz = it.next();
                Constructor<?>[] ctors = clz.getDeclaredConstructors();
                Constructor<?> ctor;
                if (ctors.length > 1)
                {
                    ctor = Arrays.stream(ctors)
                            .filter(c -> c.getAnnotation(Inject.class) != null)
                            .findAny()
                            .orElse(null);

                    if (ctor == null)
                    {
                        throw new RuntimeException("Extension classes must have one contructor or a constructor annotated with: " + Inject.class + ". Class: " + clz + " have " + ctors.length);
                    }
                }
                else
                {
                    ctor = ctors[0];
                }

                Class<?>[] parameterTypes = ctor.getParameterTypes();
                Object[] args = getConstructorArgs(parameterTypes);
                if (args == null)
                {
                    continue;
                }

                List<Class<?>> ifaces = Arrays.stream(clz.getInterfaces())
                        .filter(c -> IExtension.class.isAssignableFrom(c))
                        .collect(toList());

                Object instance;
                try
                {
                    ctor.setAccessible(true);
                    instance = ctor.newInstance(args);
                }
                catch (Exception e)
                {
                    throw new RuntimeException("Error instantiating class " + clz, e);
                }

                // Beans only implementing IExtension should not be registered as IExtension
                // only by it'c class
                if (!(ifaces.size() == 1
                        && ifaces.get(0) == IExtension.class))
                {
                    for (Class<?> iface : ifaces)
                    {
                        register(iface, instance);
                    }
                }
                // Register the class itself to let it be injectable
                register(instance);
                it.remove();
                continue;
            }

            // Done!
            if (extensionClasses.size() == 0)
            {
                break;
            }
            // No new wiring could be made then abort
            else if (count == extensionClasses.size())
            {
                throw new RuntimeException("Could not wire extensions: " + extensionClasses);
            }
        }
    }

    private Object[] getConstructorArgs(Class<?>[] parameterTypes)
    {
        if (parameterTypes.length == 0)
        {
            return ArrayUtils.EMPTY_OBJECT_ARRAY;
        }

        Object[] params = new Object[parameterTypes.length];
        int index = 0;
        for (Class<?> clz : parameterTypes)
        {
            if (!services.containsKey(clz))
            {
                return null;
            }

            params[index++] = get(clz);
        }
        return params;
    }

    /** List of services mapped to a qualifier */
    private static class ServiceList extends ArrayList<Object>
    {
    }
}
