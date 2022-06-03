package com.queryeer.api.component;

import java.awt.Component;

/**
 * Definition of a component that inspects a class for {@link Property} and builds a plain table of title and components from it's property types
 */
public interface IPropertiesComponent
{
    /** Get the component */
    Component getComponent();

    /** Set enable status of component */
    void setEnabled(boolean enabled);

    /**
     * Init component with target object.
     *
     * <pre>
     * NOTE! A deep copy if the target should be provided to be able to revert changes made
     * </pre>
     */
    public void init(Object target);

    /** Return the current target/resulting model from component and marking the component as not dirty */
    public Object getTarget();
}
