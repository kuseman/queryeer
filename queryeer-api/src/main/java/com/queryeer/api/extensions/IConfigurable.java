package com.queryeer.api.extensions;

import java.awt.Component;
import java.util.function.Consumer;

/** Definition of configurable component. */
public interface IConfigurable extends IExtension
{
    public static String CATALOG = "Catalog";
    public static String OUTPUT = "Output";
    public static String OUTPUT_FORMAT = "Output Format";

    /** Get config UI component */
    Component getComponent();

    /** Return title of configurable. Is shown in options tree */
    String getTitle();

    /**
     * Configuration group. Used when showing options tree to group similar configurables together.
     * 
     * <pre>
     * Options
     *    Output           &lt;-- Group
     *      Text
     *      Table
     *    Output Format           &lt;-- Group
     *      Json
     *      Csv
     *    Catalog          &lt;-- Group
     *      Catalog1
     *      Catalog2
     * </pre>
     */
    String groupName();

    /**
     * Add a dirty sate consumer for this configurable. Should be called each time the dirty state changes.
     */
    void addDirtyStateConsumer(Consumer<Boolean> consumer);

    /**
     * Commits changes made to config component.
     * 
     * <pre>
     * Component is responsible for storing state to disk etc.
     * </pre>
     */
    default void commitChanges()
    {
    }

    /**
     * Revert any changes made to config component.
     */
    default void revertChanges()
    {
    }

    /** Order of configurable */
    default int order()
    {
        return 0;
    }
}
