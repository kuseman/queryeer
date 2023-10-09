package com.queryeer.api.editor;

import static java.util.Collections.emptyList;

import java.util.List;

import javax.swing.Action;

/** Definition of a text editor kit with various extensions like parser, completion provider etc. */
public interface ITextEditorKit
{
    /** Return syntax high light mime type */
    default String getSyntaxMimeType()
    {
        return "text/plain";
    }

    /** Return a document parser for this kit */
    default ITextEditorDocumentParser getDocumentParser()
    {
        return null;
    }

    /** Return editor kit unique actions. */
    default List<Action> getActions()
    {
        return emptyList();
    }
}
