package com.queryeer.completion;

import static java.util.Objects.requireNonNull;

import java.util.List;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.CompletionCellRenderer;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;

import com.queryeer.api.service.IEventBus;
import com.queryeer.domain.ICatalogModel;

import se.kuseman.payloadbuilder.core.execution.QuerySession;

/** Completion installer that installs parser and auto completion facilities on {@link TextEditorPane} */
public class CompletionInstaller
{
    private final CompletionRegistry registry;

    public CompletionInstaller(IEventBus eventBus)
    {
        this.registry = new CompletionRegistry(requireNonNull(eventBus, "eventBus"));
    }

    /** Install completion facility in provided text editor */
    public void install(QuerySession querySession, List<ICatalogModel> catalogs, TextEditorPane textEditor)
    {
        PLBParser parser = new PLBParser(querySession);
        PLBCompletionProvider completionProvider = new PLBCompletionProvider(registry, parser, querySession, catalogs);

        textEditor.addParser(parser);
        textEditor.getDocument()
                .addDocumentListener(parser);

        AutoCompletion ac = new AutoCompletion(completionProvider);
        ac.setListCellRenderer(new CompletionCellRenderer());
        ac.setShowDescWindow(true);
        ac.install(textEditor);

        textEditor.setToolTipSupplier(completionProvider);
    }
}
