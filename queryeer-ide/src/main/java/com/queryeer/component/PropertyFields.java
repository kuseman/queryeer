package com.queryeer.component;

import static java.util.Objects.requireNonNull;

import java.awt.Component;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.queryeer.api.component.Property;

/** Property fields for a class */
class PropertyFields
{
    private final String header;
    private final List<PropertyField> fields;

    PropertyFields(String header, List<PropertyField> fields)
    {
        this.header = header;
        this.fields = fields;
    }

    String getHeader()
    {
        return header;
    }

    List<PropertyField> getFields()
    {
        return fields;
    }

    /** A resolved property field */
    static class PropertyField
    {
        final Component component;
        final boolean isPrimitive;
        private final Property property;
        private final Method readMethod;
        private final Method writeMethod;
        private final Class<?> type;
        private PropertyDescriptor propertyDescriptor;

        PropertyField(PropertyDescriptor propertyDescriptor, Property property, Method readMethod, Component component)
        {
            this.propertyDescriptor = requireNonNull(propertyDescriptor, "propertyDescriptor");
            this.readMethod = readMethod;
            this.writeMethod = propertyDescriptor.getWriteMethod();

            this.readMethod.setAccessible(true);
            if (this.writeMethod != null)
            {
                this.writeMethod.setAccessible(true);
            }
            this.property = property;
            this.component = component;
            this.type = readMethod.getReturnType();
            this.isPrimitive = readMethod.getReturnType()
                    .isPrimitive();
        }

        Property getProperty()
        {
            return property;
        }

        String getName()
        {
            return propertyDescriptor.getName();
        }

        String getTitle()
        {
            return property != null ? property.title()
                    : propertyDescriptor.getName();
        }

        String getDescription()
        {
            return property != null ? property.description()
                    : "";
        }

        boolean isReadOnly()
        {
            return writeMethod == null;
        }

        Class<?> getType()
        {
            return type;
        }

        Object getValue(Object target)
        {
            try
            {
                return readMethod.invoke(target);
            }
            catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
            {
                throw new RuntimeException("Error getting value from property bean", e);
            }
        }

        void setValue(Object target, Object value)
        {
            if (isReadOnly())
            {
                return;
            }

            try
            {
                writeMethod.invoke(target, value);
            }
            catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
            {
                throw new RuntimeException("Error setting value to property bean", e);
            }
        }
    }
}
