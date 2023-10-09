package com.queryeer.api.editor;

import java.awt.Color;

/** Definition of a text editor with syntax highlight etc. */
public interface ITextEditor extends IEditor
{
    /** Translate input selection into a selection that fits the editor regarding ev. selection */
    TextSelection translate(TextSelection selection);

    /** Marks the provided selection in editor */
    void select(TextSelection textSelection);

    /** Highlight provided selection with provided color */
    void highlight(TextSelection selection, Color color);

    /** Force a re-parsing of text editor content */
    void parse();
}
