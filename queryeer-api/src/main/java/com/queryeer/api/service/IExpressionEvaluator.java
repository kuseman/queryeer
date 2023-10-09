package com.queryeer.api.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.queryeer.api.extensions.engine.IQueryEngine;

import se.kuseman.payloadbuilder.api.expression.IExpression;

/** Definition of a evaluator that can be used to evaluate rules in various places. */
public interface IExpressionEvaluator
{
    /** Evaluate a predicate with provided parameters. */
    default boolean evaluatePredicate(IExpression predicate, List<IQueryEngine.IState.MetaParameter> parameters)
    {
        Map<String, Object> map = new HashMap<>();
        for (IQueryEngine.IState.MetaParameter parameter : parameters)
        {
            map.put(parameter.name(), parameter.value());
        }
        return evaluatePredicate(predicate, map);
    }

    /** Evaluate a predicate with provided parameters. */
    boolean evaluatePredicate(IExpression predicate, Map<String, Object> parameters);

    /** Parses provided expression string into a {@link IExpression}. */
    IExpression parse(String expression);
}
