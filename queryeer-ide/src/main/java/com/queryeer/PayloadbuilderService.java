package com.queryeer;

import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.QueryFileModel.State;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtension.ExceptionAction;
import com.queryeer.domain.ICatalogModel;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.core.CompiledQuery;
import se.kuseman.payloadbuilder.core.Payloadbuilder;
import se.kuseman.payloadbuilder.core.QueryResult;
import se.kuseman.payloadbuilder.core.parser.AExpressionVisitor;
import se.kuseman.payloadbuilder.core.parser.AStatementVisitor;
import se.kuseman.payloadbuilder.core.parser.Expression;
import se.kuseman.payloadbuilder.core.parser.ParseException;
import se.kuseman.payloadbuilder.core.parser.QueryParser;
import se.kuseman.payloadbuilder.core.parser.QueryStatement;
import se.kuseman.payloadbuilder.core.parser.VariableExpression;

/** Class the executes queries etc. */
class PayloadbuilderService
{
    private static final VariableVisitor VARIABLES_VISITOR = new VariableVisitor();
    private static final QueryParser PARSER = new QueryParser();
    private static final AtomicInteger THREAD_ID = new AtomicInteger(1);
    private static final Executor EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime()
            .availableProcessors() * 2, r ->
            {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("QueryExecutor-#" + THREAD_ID.getAndIncrement());
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });

    /** Execute query for provider query file */
    static void executeQuery(final QueryFileView fileView, Runnable queryFinnishedCallback)
    {
        final QueryFileModel file = fileView.getFile();
        String queryString = file.getQuery(true);
        if (isBlank(queryString))
        {
            queryFinnishedCallback.run();
            return;
        }

        final OutputWriter writer = file.getOutputExtension()
                .createOutputWriter(fileView);

        if (writer == null)
        {
            queryFinnishedCallback.run();
            return;
        }

        EXECUTOR.execute(() ->
        {
            boolean completed = false;
            while (!completed)
            {
                file.setState(State.EXECUTING);

                try
                {
                    file.getQuerySession()
                            .setAbortSupplier(() -> file.getState() == State.ABORTED);
                    CompiledQuery query = Payloadbuilder.compile(queryString);
                    QueryResult queryResult = query.execute(file.getQuerySession());

                    while (queryResult.hasMoreResults())
                    {
                        if (file.getState() == State.ABORTED)
                        {
                            break;
                        }

                        queryResult.writeResult(writer);
                        // Flush after each result set
                        writer.flush();
                    }
                    completed = true;
                }
                catch (ParseException e)
                {
                    file.setError(String.format("Syntax error. Line: %d, Column: %d. %s", e.getLine(), e.getColumn(), e.getMessage()));
                    file.setParseErrorLocation(Pair.of(e.getLine(), e.getColumn() + 1));
                    file.setState(State.ERROR);
                    completed = true;
                }
                catch (Exception e)
                {
                    if (e instanceof CatalogException)
                    {
                        // Let catalog extension handle exception
                        CatalogException ce = (CatalogException) e;
                        Optional<ICatalogExtension> catalogExtension = file.getCatalogs()
                                .stream()
                                .filter(c -> Objects.equals(ce.getCatalogAlias(), c.getAlias()))
                                .map(ICatalogModel::getCatalogExtension)
                                .findFirst();

                        if (catalogExtension.isPresent()
                                && catalogExtension.get()
                                        .handleException(file.getQuerySession(), ce) == ExceptionAction.RERUN)
                        {
                            // Re-run query
                            continue;
                        }
                    }

                    String message = e.getMessage();
                    if (e.getCause() != null)
                    {
                        message += " (" + e.getCause()
                                .getClass()
                                .getSimpleName() + ")";
                    }
                    file.setError(message);
                    file.setState(State.ERROR);
                    if (System.getProperty("devEnv") != null)
                    {
                        e.printStackTrace();
                    }
                    completed = true;
                }
                finally
                {
                    writer.close();
                    if (file.getState() == State.EXECUTING)
                    {
                        file.setState(State.COMPLETED);
                    }
                    queryFinnishedCallback.run();
                }
            }
        });
    }

    /** Get named parameters from query. */
    static Set<String> getVariables(String query)
    {
        QueryStatement parsedQuery;
        try
        {
            parsedQuery = PARSER.parseQuery(query);
        }
        catch (Exception e)
        {
            // TODO: notify error parsing
            return emptySet();
        }
        Set<String> parameters = new HashSet<>();
        parsedQuery.getStatements()
                .forEach(s -> s.accept(VARIABLES_VISITOR, parameters));
        return parameters;
    }

    /** Variable visitor. */
    private static class VariableVisitor extends AStatementVisitor<Void, Set<String>>
    {
        private static final ExpressionVisitor EXPRESSION_VISITOR = new ExpressionVisitor();

        @Override
        protected void visitExpression(Set<String> context, Expression expression)
        {
            expression.accept(EXPRESSION_VISITOR, context);
        }

        /** Expression visitor. */
        private static class ExpressionVisitor extends AExpressionVisitor<Void, Set<String>>
        {
            @Override
            public Void visit(VariableExpression expression, Set<String> context)
            {
                context.add(expression.getName());
                return null;
            }
        }
    }
}
