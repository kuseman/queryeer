package com.queryeer.editor;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;

/** Model of a query short cut */
class TextEditorQueryShortcut
{
    @JsonProperty
    private OutputType output;

    @JsonProperty
    private Object query = "";

    @JsonProperty
    private List<TextEditorQueryShortcutOverride> overrides = emptyList();

    public OutputType getOutput()
    {
        return output;
    }

    public void setOutput(OutputType output)
    {
        this.output = output;
    }

    @JsonIgnore
    public String getQuery()
    {
        if (query == null)
        {
            return "";
        }
        if (query instanceof String)
        {
            return (String) query;
        }
        else if (query instanceof Collection)
        {
            return ((Collection<?>) query).stream()
                    .map(Object::toString)
                    .collect(joining(" "));
        }
        return String.valueOf(query);
    }

    public void setQuery(Object query)
    {
        if (!(query instanceof Collection))
        {
            this.query = new ArrayList<>(asList(String.valueOf(query)
                    .split(System.lineSeparator())));
        }
        else
        {
            this.query = query;
        }
    }

    @JsonIgnore
    public List<TextEditorQueryShortcutOverride> getOverrides()
    {
        return overrides;
    }

    public void setOverrides(List<TextEditorQueryShortcutOverride> overrides)
    {
        this.overrides = overrides;
    }
}
