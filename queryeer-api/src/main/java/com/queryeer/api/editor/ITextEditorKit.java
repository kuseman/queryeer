package com.queryeer.api.editor;

import static java.util.Collections.emptyMap;

import java.util.Map;

import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.extensions.engine.IQueryEngine;

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

    /**
     * Returns the query engine that owns this text editor kit.
     */
    IQueryEngine getQueryEngine();

    /**
     * Returns the query shortcut parameters that should be used when evaluating a query shortcut override rule
     *
     * @return Returns map of query parameters available for this editor kit. If no parameters are available cause of non initialized state etc. a map with dummy values should be returned to let
     * framework know the names of available parameters in UI's etc.
     */
    default Map<String, Object> getQueryShortcutRuleParameters()
    {
        return emptyMap();
    }

    /** Create a {@link ExecuteQueryEvent} for a shortcut query */
    default ExecuteQueryEvent getQueryShortcutQueryEvent(String query, OutputType outputType)
    {
        return null;
    }

}
