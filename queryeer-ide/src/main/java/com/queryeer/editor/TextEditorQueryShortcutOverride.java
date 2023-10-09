package com.queryeer.editor;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;

import se.kuseman.payloadbuilder.api.expression.IExpression;

/** Model of a query short cut override. */
class TextEditorQueryShortcutOverride
{
    /** Payloadbuilder expression rule. Predicate that evals to true to trigger override */
    @JsonProperty
    private Object rule = "";
    /** Which query engine this rule should be evaluated against */
    @JsonProperty
    private String queryEngineClassName = "";

    /** Overridden query */
    @JsonProperty
    private Object query = "";

    @JsonProperty
    private OutputType output;

    /** Parsed expression of rule */
    @JsonIgnore
    private IExpression ruleExpression;

    @JsonIgnore
    public String getRule()
    {
        if (rule == null)
        {
            return "";
        }
        if (rule instanceof String)
        {
            return (String) rule;
        }
        else if (rule instanceof Collection)
        {
            return ((Collection<?>) rule).stream()
                    .map(Object::toString)
                    .collect(joining(System.lineSeparator()));
        }
        return String.valueOf(rule);
    }

    public void setRule(Object rule)
    {
        if (!(rule instanceof Collection))
        {
            this.rule = new ArrayList<>(asList(String.valueOf(rule)
                    .split(System.lineSeparator())));
        }
        else
        {
            this.rule = rule;
        }
    }

    public String getQueryEngineClassName()
    {
        return queryEngineClassName;
    }

    public void setQueryEngineClassName(String queryEngineClassName)
    {
        this.queryEngineClassName = queryEngineClassName;
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
                    .collect(joining(System.lineSeparator()));
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

    public OutputType getOutput()
    {
        return output;
    }

    public void setOutput(OutputType output)
    {
        this.output = output;
    }

    public IExpression getRuleExpression()
    {
        return ruleExpression;
    }

    void setRuleExpression(IExpression ruleExpression)
    {
        this.ruleExpression = ruleExpression;
    }

    @Override
    public String toString()
    {
        return StringUtils.defaultIfBlank(getRule(), getQuery());
    }

    @Override
    public TextEditorQueryShortcutOverride clone()
    {
        TextEditorQueryShortcutOverride clone = new TextEditorQueryShortcutOverride();
        clone.output = this.output;
        clone.query = getQuery();
        clone.queryEngineClassName = this.queryEngineClassName;
        clone.rule = getRule();
        return clone;
    }
}
