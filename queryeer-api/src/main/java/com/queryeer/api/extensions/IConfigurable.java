package com.queryeer.api.extensions;

import java.awt.Component;
import java.util.function.Consumer;

import com.queryeer.api.service.ICryptoService;

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

    /** Return long title of configurable. Is shown in top of configuration component. */
    default String getLongTitle()
    {
        return getTitle();
    }

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
     * Add a dirty sate consumer to this configurable. Should be called each time the dirty state changes.
     */
    void addDirtyStateConsumer(Consumer<Boolean> consumer);

    /**
     * Remove a dirty sate consumer from this configurable. Should be called each time the dirty state changes.
     */
    void removeDirtyStateConsumer(Consumer<Boolean> consumer);

    /**
     * Commits changes made to configurable.
     * 
     * <pre>
     * Configurable is responsible for storing state to disk etc.
     * </pre>
     */
    default void commitChanges()
    {
    }

    /**
     * Revert any changes made to configurable.
     */
    default void revertChanges()
    {
    }

    /**
     * Re-encrypt secrets of this configurable using provided crypto service. Implementations of this method should first decrypt it's secrets using existing {@link ICryptoService} and then encrypt
     * using provided new crypto service.
     * <p>
     * NOTE! Changes are stored/reverted as usual upon {@link #commitChanges()}/{@link IConfigurable#revertChanges()}
     * </p>
     *
     * @return Returns Result of re-encryption
     */
    default EncryptionResult reEncryptSecrets(ICryptoService newCryptoService)
    {
        return EncryptionResult.NO_CHANGE;
    }

    /** Order of configurable */
    default int order()
    {
        return 0;
    }

    /** Result of re-encryption */
    enum EncryptionResult
    {
        ABORT,
        SUCCESS,
        NO_CHANGE
    }
}
