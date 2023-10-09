package com.queryeer.editor;

import static java.util.Objects.requireNonNull;

import java.util.List;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;

import com.queryeer.api.action.IActionRegistry.KeyboardShortcut;
import com.queryeer.api.editor.IEditorFactory;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.editor.ITextEditorKit;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IExpressionEvaluator;
import com.queryeer.api.service.ITemplateService;

/** Factory for creating common Queryeer editors */
class EditorFactory implements IEditorFactory
{
    private final IEventBus eventBus;
    private final ITemplateService templateService;
    private final TextEditorQueryShortcutConfigurable queryShortcutConfigurable;
    private final IExpressionEvaluator expressionEvaluator;

    EditorFactory(IEventBus eventBus, ITemplateService templateService, TextEditorQueryShortcutConfigurable queryShortcutConfigurable, IExpressionEvaluator expressionEvaluator)
    {
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.templateService = requireNonNull(templateService, "templateService");
        this.queryShortcutConfigurable = requireNonNull(queryShortcutConfigurable, "queryShortcutConfigurable");
        this.expressionEvaluator = requireNonNull(expressionEvaluator, "expressionEvaluator");
    }

    @Override
    public ITextEditor createTextEditor(IQueryEngine.IState engineState, ITextEditorKit editorKit)
    {
        TextEditor editor = new TextEditor(eventBus, editorKit);
        installQueryShortcuts(engineState, editor);
        return editor;
    }

    @Override
    public ITextEditor createTextEditor(ITextEditorKit editorKit)
    {
        return new TextEditor(eventBus, editorKit);
    }

    private void installQueryShortcuts(IQueryEngine.IState engineState, TextEditor editor)
    {
        ActionMap actionMap = editor.getActionMap();
        // Create actions for each query shortcut
        for (int i = 0; i < TextEditorQueryShortcutConfigurable.QUERY_SHORTCUT_COUNT; i++)
        {
            actionMap.put(queryShortcutConfigurable.getKeyboardShortcutName(i),
                    new QueryShortcutAction(queryShortcutConfigurable, templateService, engineState, editor, eventBus, expressionEvaluator, i));
        }

        bindQueryShortcuts(editor);

        // TODO: action registry change event
    }

    private void bindQueryShortcuts(TextEditor editor)
    {
        InputMap inputMap = editor.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.clear();

        for (int i = 0; i < TextEditorQueryShortcutConfigurable.QUERY_SHORTCUT_COUNT; i++)
        {
            String shortcutName = queryShortcutConfigurable.getKeyboardShortcutName(i);
            List<KeyboardShortcut> keyboardShortcut = queryShortcutConfigurable.getKeyboardShortcut(i);
            for (KeyboardShortcut shortcut : keyboardShortcut)
            {
                if (shortcut.keyStroke2() != null)
                {
                    throw new IllegalArgumentException("Double key strokes are not supported");
                }
                inputMap.put(shortcut.keyStroke1(), shortcutName);
            }
        }
    }
}
