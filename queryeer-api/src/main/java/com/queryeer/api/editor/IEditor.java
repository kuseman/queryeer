package com.queryeer.api.editor;

import java.beans.PropertyChangeListener;
import java.io.File;

import javax.swing.JComponent;

/** Base definition of an editor */
public interface IEditor
{
    /** Dirty property of editor. Is fired once when the editor becomes dirty */
    public static final String DIRTY = "dirty";

    /** Load content into editor from provided file */
    void loadFromFile(File file);

    /** Save content in editor to provided file */
    void saveToFile(File file);

    /** Set value to editor. If editor doesn't support provided value {@link IllegalArgumentException} is thrown. */
    void setValue(Object value);

    /** Get default value from editor. */
    Object getValue();

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

    // /**
    // * Get actions for this editor. Actions can be placed in toolbar and/or in menu depending on properties: - {@link #ACTION_SHOW_IN_MENU} - {@link #ACTION_SHOW_IN_TOOLBAR} -
    // * {@link #ACTION_MENU_PATH}
    // */
    // default List<Action> getActions()
    // {
    // return emptyList();
    // }

    /** Return component for this editor */
    JComponent getComponent();

}
