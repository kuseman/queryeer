package com.queryeer.api.extensions.assistant;

/**
 * A selectable context item provided by a query engine for inclusion in the AI system prompt. Represents a schema object (table, view, index, etc.) or other engine-specific context that users can
 * pick in the AI chat window.
 */
public interface IAIContextItem
{
    /** Display label shown in the context picker (e.g. "dbo.Orders") */
    String getLabel();

    /** Group name for organizing items in the picker (e.g. "Tables", "Views") */
    String getGroup();

    /** Text to append to the system prompt when this item is selected */
    String getContent();

    /**
     * Return true if this item should be pre-selected by default when the context picker is opened with no prior selection (e.g. the engine detected this object is referenced in the current query).
     */
    default boolean isDefaultSelected()
    {
        return false;
    }
}
