package com.queryeer;

import java.util.Map;

import com.queryeer.api.service.IExpressionEvaluator;

import se.kuseman.payloadbuilder.api.expression.IExpression;
import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;
import se.kuseman.payloadbuilder.core.execution.ExecutionContext;
import se.kuseman.payloadbuilder.core.execution.QuerySession;
import se.kuseman.payloadbuilder.core.logicalplan.optimization.LogicalPlanOptimizer;
import se.kuseman.payloadbuilder.core.parser.QueryParser;

class ExpressionEvaluator implements IExpressionEvaluator
{
    private static final CatalogRegistry REGISTRY = new CatalogRegistry();
    private static final QueryParser PARSER = new QueryParser();

    @Override
    public boolean evaluatePredicate(IExpression predicate, Map<String, Object> parameters)
    {
        QuerySession session = new QuerySession(REGISTRY, parameters);
        ExecutionContext context = new ExecutionContext(session);

        return predicate.eval(context)
                .getPredicateBoolean(0);
    }

    @Override
    public IExpression parse(String expression)
    {
        IExpression exp = PARSER.parseExpression(expression);
        ExecutionContext context = new ExecutionContext(new QuerySession(new CatalogRegistry()));
        return LogicalPlanOptimizer.resolveExpression(context, exp);
    }
}
