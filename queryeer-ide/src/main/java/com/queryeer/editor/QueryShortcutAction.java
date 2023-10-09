package com.queryeer.editor;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.event.ActionEvent;
import java.util.Map;

import javax.swing.AbstractAction;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IExpressionEvaluator;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.api.expression.IExpression;

class QueryShortcutAction extends AbstractAction
{
    private final TextEditorQueryShortcutConfigurable queryShortcutConfigurable;
    private final ITemplateService templateService;
    private final IQueryEngine.IState engineState;
    private final TextEditor textEditor;
    private final IEventBus eventBus;
    private final IExpressionEvaluator expressionEvaluator;
    private final int shortcutIndex;

    QueryShortcutAction(TextEditorQueryShortcutConfigurable queryShortcutConfigurable, ITemplateService templateService, IQueryEngine.IState engineState, TextEditor textEditor, IEventBus eventBus,
            IExpressionEvaluator expressionEvaluator, int shortcutIndex)
    {
        this.queryShortcutConfigurable = queryShortcutConfigurable;
        this.templateService = templateService;
        this.engineState = engineState;
        this.textEditor = textEditor;
        this.eventBus = eventBus;
        this.expressionEvaluator = expressionEvaluator;
        this.shortcutIndex = shortcutIndex;
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        String selectedText = textEditor.getSelectedText();
        if (isBlank(selectedText))
        {
            return;
        }

        TextEditorQueryShortcut shortcut = queryShortcutConfigurable.getQueryShortcut(shortcutIndex);
        if (shortcut == null)
        {
            return;
        }

        OutputType output = shortcut.getOutput();
        String queryTemplate = shortcut.getQuery();
        if (!shortcut.getOverrides()
                .isEmpty())
        {
            String queryEngineClass = engineState.getQueryEngine()
                    .getClass()
                    .getSimpleName();

            for (TextEditorQueryShortcutOverride override : shortcut.getOverrides())
            {
                if (isBlank(override.getQueryEngineClassName())
                        || !StringUtils.equalsIgnoreCase(queryEngineClass, override.getQueryEngineClassName()))
                {
                    continue;
                }
                IExpression rule = override.getRuleExpression();
                if (rule == null)
                {
                    queryTemplate = override.getQuery();
                    output = ObjectUtils.defaultIfNull(override.getOutput(), output);
                }
                else
                {
                    if (expressionEvaluator.evaluatePredicate(rule, engineState.getMetaParameters()))
                    {
                        queryTemplate = override.getQuery();
                        output = ObjectUtils.defaultIfNull(override.getOutput(), output);
                        break;
                    }

                }
            }
        }

        if (isBlank(queryTemplate))
        {
            return;
        }

        output = ObjectUtils.defaultIfNull(output, OutputType.TABLE);

        String query = templateService.process("TextEditor.QueryShortcut", queryTemplate, Map.of("selectedText", selectedText));
        ExecuteQueryEvent queryEvent = engineState.getQueryEngine()
                .getExecuteQueryEvent(query, output);
        if (queryEvent != null)
        {
            eventBus.publish(queryEvent);
        }
        else
        {
            throw new RuntimeException(engineState.getQueryEngine()
                    .getClass()
                    .getSimpleName() + " does not support query shortcuts.");
        }
    }

}