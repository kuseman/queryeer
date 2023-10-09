package com.queryeer.api.action;

import static java.util.Objects.requireNonNull;

import java.util.List;

import javax.swing.KeyStroke;

/**
 * Definition of a short cut registry. Manages short cuts for actions and notifies when short cuts has changed etc. Idea here is that extension can register actions and their default shortcuts and the
 * Queryeer can remap those shortcuts and make them available as customizations by user
 */
public interface IActionRegistry
{
    /**
     * Register an action to registry
     *
     * @param name Unique action name
     * @param scope What scope has this action
     * @param defaultKeyboardShortcuts The actions default keyboard shortcut
     */
    void register(String name, ActionScope scope, List<KeyboardShortcut> defaultKeyboardShortcuts);

    /** Add a change listener to the registry. Listeners will be notified when any action has changes it's shortcuts */
    void addChangeListener(Runnable listener);

    /** Return shortcut with provided name */
    List<KeyboardShortcut> getKeyboardShortcut(String name);

    /** Scope of action. */
    enum ActionScope
    {
        /** Action is executed in a global fashion. It's triggered no matter which editor has focus etc. */
        GLOBAL,

        /** Action is executed only when it's owning component has focus */
        COMPONENT_FOCUS
    }

    /** Definition of a shortcut. One or two key strokes that's pressed in order */
    record KeyboardShortcut(KeyStroke keyStroke1, KeyStroke keyStroke2)
    {
        public KeyboardShortcut(KeyStroke keyStroke)
        {
            this(requireNonNull(keyStroke), null);
        }
    }
}
