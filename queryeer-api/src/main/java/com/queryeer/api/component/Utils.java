package com.queryeer.api.component;

import static java.util.Collections.emptyMap;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.ImageIcon;

import com.queryeer.api.component.PropertyFields.PropertyField;

/** Component utils */
class Utils
{
    static ImageIcon getResouceIcon(String name)
    {
        return new ImageIcon(Utils.class.getResource(name));
    }

    /**
     * Collect property fields from provided class. First tries to find {@link Property} on get methods if no methods is found try {@link java.beans.Introspector}.
     */
    static PropertyFields collectPropertyFields(Class<?> clazz)
    {
        try
        {
            boolean isPropertyAware = Arrays.stream(clazz.getInterfaces())
                    .anyMatch(i -> IPropertyAware.class == i);

            Properties properties = clazz.getAnnotation(Properties.class);
            Map<String, Property> propertyByName = properties != null ? Arrays.stream(properties.properties())
                    .collect(Collectors.toMap(p -> p.propertyName(), Function.identity()))
                    : emptyMap();

            BeanInfo beanInfo = Introspector.getBeanInfo(clazz);

            List<PropertyField> result = new ArrayList<>();

            for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors())
            {
                if (pd.getReadMethod() == null)
                {
                    continue;
                }

                if ("getClass".equals(pd.getReadMethod()
                        .getName()))
                {
                    continue;
                }

                Property property = pd.getReadMethod()
                        .getAnnotation(Property.class);
                if (property == null)
                {
                    property = propertyByName.get(pd.getName());
                }

                if (property != null
                        && property.ignore())
                {
                    continue;
                }

                if (property != null
                        && (property.enableAware()
                                || property.visibleAware())
                        && !isPropertyAware)
                {
                    throw new IllegalArgumentException(clazz + " must implement " + IPropertyAware.class + " to be able to have properties with enable/visible awareness");
                }

                result.add(new PropertyField(pd, property));
            }

            for (MethodDescriptor method : beanInfo.getMethodDescriptors())
            {
                Property property = method.getMethod()
                        .getAnnotation(Property.class);
                if (property == null
                        || method.getMethod()
                                .getReturnType() != Void.class)
                {
                    continue;
                }

                result.add(new PropertyField(method, property));
            }

            result.sort((a, b) ->
            {
                if (a.getProperty() == null
                        && b.getProperty() == null)
                {
                    return String.CASE_INSENSITIVE_ORDER.compare(a.getTitle(), b.getTitle());
                }
                if (a.getProperty() == null)
                {
                    return 1;
                }
                else if (b.getProperty() == null)
                {
                    return -1;
                }

                return Integer.compare(a.getProperty()
                        .order(),
                        b.getProperty()
                                .order());
            });

            if (result.size() == 0)
            {
                throw new RuntimeException("Could not find any properites on class " + clazz);
            }

            String header = properties != null ? properties.header()
                    : "";
            return new PropertyFields(header, result);
        }
        catch (IntrospectionException e)
        {
            throw new IllegalArgumentException("Error inspecting bean class: " + clazz, e);
        }
    }

    /** Create new instance of provided class using no params constructor */
    static Object newInstance(Class<?> clazz)
    {
        Constructor<?> ctor = Arrays.stream(clazz.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 0)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Could not find a no parameter constructor for class ) + clazz"));
        ctor.setAccessible(true);
        try
        {
            return ctor.newInstance();
        }
        catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
        {
            throw new IllegalArgumentException("Error creating instance of class: " + clazz, e);
        }
    }
}
