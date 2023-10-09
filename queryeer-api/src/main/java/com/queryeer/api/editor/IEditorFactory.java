package com.queryeer.api.editor;

import com.queryeer.api.extensions.IExtension;
import com.queryeer.api.extensions.engine.IQueryEngine;

/** Editor factory for creating {@link IEditor}'s */
public interface IEditorFactory extends IExtension
{
    /** Creates a {@link ITextEditor} with provided editor kit. */
    ITextEditor createTextEditor(IQueryEngine.IState engineState, ITextEditorKit editorKit);

    /** Creates a {@link ITextEditor} with provided editor kit. */
    ITextEditor createTextEditor(ITextEditorKit editorKit);
}
