package com.queryeer.api.editor;

import java.beans.PropertyChangeListener;
import java.io.File;

import javax.swing.JComponent;

/** Base definition of an editor */
public interface IEditor
{
    /** Dirty property of editor. Is fired once when the editor becomes dirty. */
    public static final String DIRTY = "dirty";
    /**
     * Value changed property of editor. Is fired when the editors value changes. NOTE! The old/new value is not provided during event firing, this is only a marker.
     */
    public static final String VALUE_CHANGED = "valueChanged";

    /** Load content into editor from provided file */
    void loadFromFile(File file);

    /** Save content in editor to provided file */
    void saveToFile(File file, boolean notifyDirty);

    /** Set value to editor. If editor doesn't support provided value {@link IllegalArgumentException} is thrown. */
    void setValue(Object value);

    /**
     * Get default value from editor.
     *
     * @param raw True if no modifications like selections etc. should be applied otherwise false.
     */
    Object getValue(boolean raw);

    /** Returns true if this editor's value is empty {@see #getValue(boolean)} */
    boolean isValueEmpty();

    /** Set editor dirty state */
    void setDirty(boolean dirty);

    /** Add dirty sate listener to this editor */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /** Remove dirty sate listener from this editor */
    void removePropertyChangeListener(PropertyChangeListener listener);

    /** Called when this editor got focus when switched editor tab */
    void focused();

    /** Clear any eventual sates before execution */
    default void clearBeforeExecution()
    {
    }

    /** Return component for this editor */
    JComponent getComponent();

}
