package com.queryeer.api.component;

import java.awt.Component;
import java.util.List;

/**
 * Component with a editable list of items and a {@link PropertiesComponent} for selected item.
 */
public interface IListPropertiesComponent<T>
{
    /** Return the component */
    Component getComponent();

    /**
     * Init component with target list.
     *
     * <pre>
     * NOTE! A deep copy if the list should be provided to be able to revert changes made
     * </pre>
     */
    void init(List<T> target);

    /** Get resulting list of items */
    List<T> getResult();
}
