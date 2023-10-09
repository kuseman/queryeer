package com.queryeer.api.component;

import static java.util.Objects.requireNonNull;

import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

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
        private final Property property;
        private final Method readMethod;
        private final Method writeMethod;
        private final Class<?> type;
        private final String name;
        private final String title;

        PropertyField(PropertyDescriptor propertyDescriptor, Property property)
        {
            this.readMethod = propertyDescriptor.getReadMethod();
            this.writeMethod = propertyDescriptor.getWriteMethod();
            this.name = propertyDescriptor.getName();
            this.title = property != null ? property.title()
                    : propertyDescriptor.getName();

            this.readMethod.setAccessible(true);
            if (this.writeMethod != null)
            {
                this.writeMethod.setAccessible(true);
            }
            this.property = property;
            this.type = readMethod.getReturnType();
        }

        PropertyField(MethodDescriptor methodDescriptor, Property property)
        {
            this.readMethod = methodDescriptor.getMethod();
            this.readMethod.setAccessible(true);
            this.writeMethod = null;
            this.property = requireNonNull(property, "property");
            this.name = this.readMethod.getName();
            this.title = property.title();
            this.type = Void.class;
        }

        Property getProperty()
        {
            return property;
        }

        String getName()
        {
            return name;
        }

        String getTitle()
        {
            return title;
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

        boolean isOperation()
        {
            return type == Void.class;
        }

        Class<?> getType()
        {
            return type;
        }

        Object getValue(Object target)
        {
            if (isOperation())
            {
                return null;
            }

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
                // Convert null to default primitive value
                if (type.isPrimitive()
                        && value == null)
                {
                    value = Array.get(Array.newInstance(type, 1), 0);
                }

                writeMethod.invoke(target, value);
            }
            catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
            {
                throw new RuntimeException("Error setting value to property bean", e);
            }
        }

        void call(Object target)
        {
            if (target == null)
            {
                return;
            }

            try
            {
                readMethod.invoke(target);
            }
            catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
            {
                throw new RuntimeException("Error calling operation", e);
            }
        }
    }
}
