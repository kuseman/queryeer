package com.queryeer;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;

import com.queryeer.api.action.IActionRegistry;
import com.queryeer.api.extensions.IConfigurable;

/** Action registry implementation */
class ActionRegistry implements IActionRegistry, IConfigurable
{
    private final Map<String, QueryeerAction> actions = new ConcurrentHashMap<>();
    private final List<Consumer<Boolean>> dirtyConsumers = new ArrayList<>();
    private final List<Runnable> actionChangeListeners = new ArrayList<>();
    private JPanel component;

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new JPanel(new BorderLayout());

            DefaultTableModel model = new DefaultTableModel(new String[] { "Key", "Shortcut", "Scope" }, 0)
            {
                @Override
                public boolean isCellEditable(int row, int column)
                {
                    return false;
                }
            };

            for (QueryeerAction action : actions.values())
            {
                model.addRow(new Object[] { action.name, action.getShortcutString(), action.scope });
            }

            JTable table = new JTable();
            table.setModel(model);
            component.add(new JScrollPane(table), BorderLayout.CENTER);
        }
        return component;
    }

    @Override
    public String getTitle()
    {
        return "Actions";
    }

    @Override
    public String groupName()
    {
        return "Queryeer";
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyConsumers.add(consumer);
    }

    @Override
    public void removeDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyConsumers.remove(consumer);
    }

    @Override
    public void register(String name, ActionScope scope, List<KeyboardShortcut> defaultKeyboardShortcuts)
    {
        if (actions.put(requireNonNull(name).toLowerCase(), new QueryeerAction(name, scope, defaultKeyboardShortcuts)) != null)
        {
            throw new IllegalArgumentException("Action with name " + name + " is already registered");
        }
    }

    @Override
    public void addChangeListener(Runnable listener)
    {
        actionChangeListeners.add(listener);
    }

    @Override
    public List<KeyboardShortcut> getKeyboardShortcut(String name)
    {
        return actions.get(requireNonNull(name).toLowerCase()).keyboardShortcuts;
    }

    static class QueryeerAction
    {
        private final String name;
        private final ActionScope scope;

        /** An actions current keyboard shortcuts */
        private List<KeyboardShortcut> keyboardShortcuts;

        QueryeerAction(String name, ActionScope scope)
        {
            this(name, scope, emptyList());
        }

        QueryeerAction(String name, ActionScope scope, List<KeyboardShortcut> keyboardShortcuts)
        {
            this.name = name;
            this.scope = scope;
            this.keyboardShortcuts = keyboardShortcuts;
        }

        void setKeyboardShortcut(List<KeyboardShortcut> keyboardShortcuts)
        {
            this.keyboardShortcuts = keyboardShortcuts;
        }

        String getShortcutString()
        {
            return keyboardShortcuts.stream()
                    .map(s ->
                    {
                        String txt = getAcceleratorText(s.keyStroke1());
                        if (s.keyStroke2() != null)
                        {
                            txt += "+" + getAcceleratorText(s.keyStroke2());
                        }
                        return txt;
                    })
                    .collect(joining(","));
        }
    }

    private static String getAcceleratorText(KeyStroke accelerator)
    {
        String acceleratorText = "";
        if (accelerator != null)
        {
            int modifiers = accelerator.getModifiers();
            if (modifiers > 0)
            {
                acceleratorText = InputEvent.getModifiersExText(modifiers);
                acceleratorText += "+";
            }
            acceleratorText += KeyEvent.getKeyText(accelerator.getKeyCode());
        }
        return acceleratorText;
    }
}
