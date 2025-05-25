package com.queryeer.api.extensions.output.text;

import java.io.PrintWriter;

import com.queryeer.api.editor.TextSelection;
import com.queryeer.api.extensions.output.IOutputComponent;

/** Definition of the built in text output component */
public interface ITextOutputComponent extends IOutputComponent
{
    /** Return the print writer for this component */
    PrintWriter getTextWriter();

    /** Appends a warning to text output. */
    void appendWarning(String message, TextSelection textSelection);

    /** Appends a link to text output. This creates an underlined/clickable text that triggers provided action upon click. */
    void appendLink(String message, Runnable action);
}
