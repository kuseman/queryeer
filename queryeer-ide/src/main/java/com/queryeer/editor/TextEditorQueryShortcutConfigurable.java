package com.queryeer.editor;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import com.queryeer.api.action.IActionRegistry;
import com.queryeer.api.action.IActionRegistry.ActionScope;
import com.queryeer.api.action.IActionRegistry.KeyboardShortcut;
import com.queryeer.api.component.IJsonEditorComponentFactory;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.service.IConfig;

import se.kuseman.payloadbuilder.api.expression.IExpression;
import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;
import se.kuseman.payloadbuilder.core.execution.ExecutionContext;
import se.kuseman.payloadbuilder.core.execution.QuerySession;
import se.kuseman.payloadbuilder.core.logicalplan.optimization.LogicalPlanOptimizer;
import se.kuseman.payloadbuilder.core.parser.QueryParser;

/** Configurable of query shortcuts */
class TextEditorQueryShortcutConfigurable implements IConfigurable
{
    private static final String NAME = "com.queryeer.editor.TextEditorQueryShortcuts";
    static final String SHORTCUT_PREFIX = "TextEditor.QueryShortcut.";
    static final int QUERY_SHORTCUT_COUNT = 12;
    private static final QueryParser PARSER = new QueryParser();

    private final IJsonEditorComponentFactory.IJsonEditorComponent<Shortcuts> editor;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();
    private final IConfig config;
    private final IActionRegistry actionRegistry;

    private JPanel component;

    /** Result value */
    private Shortcuts shortcuts;

    TextEditorQueryShortcutConfigurable(IJsonEditorComponentFactory jsonEditorComponentFactory, IConfig config, IActionRegistry actionRegistry)
    {
        this.editor = requireNonNull(jsonEditorComponentFactory, "jsonEditorComponentFactory").create(Shortcuts.class, this::verify);
        this.config = requireNonNull(config, "config");
        this.actionRegistry = requireNonNull(actionRegistry, "actionRegistry");

        // Load and parse content
        editor.load(config.getConfigFileName(NAME));
        shortcuts = editor.getResult();
        if (shortcuts != null)
        {
            shortcuts.parseExpressions();
        }

        // Register 12 query shortcuts
        for (int i = 0; i < QUERY_SHORTCUT_COUNT; i++)
        {
            KeyStroke keyStroke = null;
            // Default to CTRL + (0-9) AND ALT + F1 AND CTRL + F1
            if (i < 10)
            {
                keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, Toolkit.getDefaultToolkit()
                        .getMenuShortcutKeyMaskEx());
            }
            else if (i == 10)
            {
                keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F1, KeyEvent.CTRL_DOWN_MASK);
            }
            else if (i == 11)
            {
                keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F1, KeyEvent.ALT_DOWN_MASK);
            }
            actionRegistry.register(SHORTCUT_PREFIX + i, ActionScope.COMPONENT_FOCUS, List.of(new KeyboardShortcut(keyStroke)));
        }
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new JPanel();
            component.setLayout(new GridBagLayout());

            JLabel label = new JLabel("<html><h2>Shortcuts</h2><hr></html>");
            label.setHorizontalAlignment(JLabel.CENTER);
            component.add(label, new GridBagConstraints(0, 0, 2, 1, 1.0d, 0.0d, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
            component.add(editor.getComponent(), new GridBagConstraints(0, 1, 2, 1, 1.0d, 1.0d, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

            editor.addPropertyChangeListener(new PropertyChangeListener()
            {
                @Override
                public void propertyChange(PropertyChangeEvent evt)
                {
                    if (IJsonEditorComponentFactory.IJsonEditorComponent.CONTENT.equalsIgnoreCase(evt.getPropertyName()))
                    {
                        dirtyStateConsumers.forEach(c -> c.accept(true));
                    }
                }
            });
        }
        return component;
    }

    @Override
    public String getTitle()
    {
        return "Query Shortcuts";
    }

    @Override
    public String groupName()
    {
        return "Text Editor";
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyStateConsumers.add(consumer);
    }

    @Override
    public void removeDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyStateConsumers.remove(consumer);
    }

    @Override
    public void commitChanges()
    {
        File file = config.getConfigFileName(NAME);
        editor.save(file);
        shortcuts = editor.getResult();
        if (shortcuts != null)
        {
            shortcuts.parseExpressions();
        }
    }

    @Override
    public void revertChanges()
    {
        File file = config.getConfigFileName(NAME);
        editor.load(file);
    }

    String getKeyboardShortcutName(int shortcutNumber)
    {
        if (shortcutNumber < 0
                || shortcutNumber >= QUERY_SHORTCUT_COUNT)
        {
            throw new IllegalArgumentException("Invalid shortcut number");
        }
        return SHORTCUT_PREFIX + shortcutNumber;
    }

    List<KeyboardShortcut> getKeyboardShortcut(int shortcutNumber)
    {
        if (shortcutNumber < 0
                || shortcutNumber >= QUERY_SHORTCUT_COUNT)
        {
            throw new IllegalArgumentException("Invalid shortcut number");
        }

        String key = SHORTCUT_PREFIX + shortcutNumber;
        return actionRegistry.getKeyboardShortcut(key);
    }

    List<TextEditorQueryShortcut> getQueryShortcuts()
    {
        if (shortcuts == null)
        {
            return emptyList();
        }

        return shortcuts.shortcuts;
    }

    private record Shortcuts(List<TextEditorQueryShortcut> shortcuts)
    {
        void parseExpressions()
        {
            shortcuts.stream()
                    .flatMap(s -> s.getOverrides()
                            .stream())
                    .filter(o -> !isBlank(o.getRule()))
                    .forEach(o ->
                    {
                        try
                        {
                            o.setRuleExpression(getExpression(o.getRule()));
                        }
                        catch (Exception e)
                        {
                            JOptionPane.showMessageDialog(null, "Error parsing rule: '" + abbreviate(o.getQuery(), 30) + "', error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
        }
    }

    private String verify(Shortcuts shortcuts)
    {
        Map<String, IQueryEngine> engineNames = config.getQueryEngines()
                .stream()
                .collect(toMap(en -> en.getClass()
                        .getSimpleName()
                        .toLowerCase(), Function.identity()));

        for (TextEditorQueryShortcut shortcut : shortcuts.shortcuts)
        {
            for (TextEditorQueryShortcutOverride override : shortcut.getOverrides())
            {
                IQueryEngine engine = engineNames.get(Objects.toString(override.getQueryEngineClassName(), "")
                        .toLowerCase());

                if (engine == null)
                {
                    return "Overrides must have a valid query engine class name. Available engines: " + engineNames.values() + ", got: " + override.getQueryEngineClassName();
                }

                if (!isBlank(override.getRule()))
                {
                    try
                    {
                        PARSER.parseExpression(override.getRule());
                    }
                    catch (Exception ee)
                    {
                        return "Could not parse rule expression: " + override.getRule() + ", error: " + ee.getMessage();
                    }
                }
            }
        }

        return "";
    };

    static IExpression getExpression(String expression)
    {
        IExpression exp = PARSER.parseExpression(expression);
        ExecutionContext context = new ExecutionContext(new QuerySession(new CatalogRegistry()));
        return LogicalPlanOptimizer.resolveExpression(context, exp);
    }
}
