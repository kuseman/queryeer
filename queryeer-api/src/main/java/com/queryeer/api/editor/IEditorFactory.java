package com.queryeer.api.editor;

import com.queryeer.api.extensions.IExtension;

/** Editor factory for creating {@link IEditor}'s */
public interface IEditorFactory extends IExtension
{
    /** Creates a {@link ITextEditor} with provided editor kit. */
    ITextEditor createTextEditor(ITextEditorKit editorKit);
}
