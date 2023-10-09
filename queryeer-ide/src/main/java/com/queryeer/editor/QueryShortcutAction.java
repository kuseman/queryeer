package com.queryeer.editor;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.event.ActionEvent;
import java.util.Map;

import javax.swing.AbstractAction;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.api.expression.IExpression;
import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;
import se.kuseman.payloadbuilder.core.execution.ExecutionContext;
import se.kuseman.payloadbuilder.core.execution.QuerySession;

class QueryShortcutAction extends AbstractAction
{
    private static final CatalogRegistry REGISTRY = new CatalogRegistry();
    private final TextEditorQueryShortcutConfigurable queryShortcutConfigurable;
    private final ITemplateService templateService;
    private final TextEditor textEditor;
    private final IEventBus eventBus;
    private final int shortcutNumber;

    QueryShortcutAction(TextEditorQueryShortcutConfigurable queryShortcutConfigurable, ITemplateService templateService, TextEditor textEditor, IEventBus eventBus, int shortcutNumber)
    {
        this.queryShortcutConfigurable = queryShortcutConfigurable;
        this.templateService = templateService;
        this.textEditor = textEditor;
        this.eventBus = eventBus;
        this.shortcutNumber = shortcutNumber;
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        String selectedText = textEditor.getSelectedText();
        if (isBlank(selectedText))
        {
            return;
        }

        if (shortcutNumber >= queryShortcutConfigurable.getQueryShortcuts()
                .size())
        {
            return;
        }

        TextEditorQueryShortcut shortcut = queryShortcutConfigurable.getQueryShortcuts()
                .get(shortcutNumber);

        OutputType output = shortcut.getOutput();
        String queryTemplate = shortcut.getQuery();
        if (!shortcut.getOverrides()
                .isEmpty())
        {
            String queryEngineClass = textEditor.editorKit.getQueryEngine()
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
                    Map<String, Object> parameters = textEditor.editorKit.getQueryShortcutRuleParameters();
                    QuerySession session = new QuerySession(REGISTRY, parameters);
                    ExecutionContext context = new ExecutionContext(session);

                    if (rule.eval(context)
                            .getPredicateBoolean(0))
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
        ExecuteQueryEvent queryEvent = this.textEditor.editorKit.getQueryShortcutQueryEvent(query, output);
        if (queryEvent != null)
        {
            eventBus.publish(queryEvent);
        }
        else
        {
            throw new RuntimeException(textEditor.editorKit.getQueryEngine()
                    .getClass()
                    .getSimpleName() + " does not support query shortcuts.");
        }
    }

}