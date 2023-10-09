package com.queryeer.api.extensions.output;

import java.awt.Component;

import javax.swing.Icon;

/** Definition of an output component */
public interface IOutputComponent
{
    /** Title that is shown in result tab component */
    String title();

    /** Return the owning extension for this component. */
    IOutputExtension getExtension();

    /**
     * Returns true if this component is active and should be populated. Only applicable if it's extension is {@link IOutputExtension#isAutoPopulated()}
     */
    default boolean active()
    {
        return true;
    }

    /** Icon that is shown in result tab component */
    default Icon icon()
    {
        return null;
    }

    /** Return the UI component */
    Component getComponent();

    /** Clear eventual state in the component */
    default void clearState()
    {
    }

    /** Dispose any resources etc. Called when a query tab is closed. */
    default void dispose()
    {
    }
}
