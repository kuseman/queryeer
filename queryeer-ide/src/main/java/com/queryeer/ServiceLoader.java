package com.queryeer;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

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
    private final Map<Class<?>, ServiceRegistration> services = new HashMap<>();
    private final Map<Class<?>, Object> resolvedInstances = new HashMap<>();

    private PluginHandler pluginHandler;

    ServiceLoader()
    {
        this.pluginHandler = new PluginHandler();
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

        for (Class<?> clz : extensionClasses)
        {
            List<Class<?>> ifaces = Arrays.stream(clz.getInterfaces())
                    .filter(c -> IExtension.class.isAssignableFrom(c))
                    .collect(toList());

            register(clz, ifaces);
        }
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
