package com.queryeer;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.queryeer.api.IQueryFile.ExecutionState;
import com.queryeer.api.extensions.IQueryProvider;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Class the executes queries etc. */
class QueryService
{
    // private static final VariableVisitor VARIABLES_VISITOR = new VariableVisitor();
    // private static final QueryParser PARSER = new QueryParser();
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
        final OutputWriter writer = file.getOutputExtension()
                .createOutputWriter(fileView);

        if (writer == null)
        {
            queryFinnishedCallback.run();
            return;
        }

        IQueryProvider queryProvider = file.getQueryFileState()
                .getQueryProvider();

        EXECUTOR.execute(() ->
        {
            file.setExecutionState(ExecutionState.EXECUTING);

            try
            {
                queryProvider.executeQuery(fileView, writer);

                // file.getQuerySession()
                // .setAbortSupplier(() -> file.getState() == State.ABORTED);
                // CompiledQuery query = Payloadbuilder.compile(queryString);
                // QueryResult queryResult = query.execute(file.getQuerySession());
                //
                // while (queryResult.hasMoreResults())
                // {
                // if (file.getState() == State.ABORTED)
                // {
                // break;
                // }
                //
                // queryResult.writeResult(writer);
                // // Flush after each result set
                // writer.flush();
                // }
                // completed = true;
            }
            // catch (ParseException e)
            // {
            // file.setError(String.format("Syntax error. Line: %d, Column: %d. %s", e.getLine(), e.getColumn(), e.getMessage()));
            // file.setParseErrorLocation(Pair.of(e.getLine(), e.getColumn() + 1));
            // file.setState(State.ERROR);
            // completed = true;
            // }
            catch (Exception e)
            {
                // if (e instanceof CatalogException)
                // {
                // // Let catalog extension handle exception
                // CatalogException ce = (CatalogException) e;
                // Optional<ICatalogExtension> catalogExtension = file.getCatalogs()
                // .stream()
                // .filter(c -> Objects.equals(ce.getCatalogAlias(), c.getAlias()))
                // .map(ICatalogModel::getCatalogExtension)
                // .findFirst();
                //
                // if (catalogExtension.isPresent()
                // && catalogExtension.get()
                // .handleException(file.getQuerySession(), ce) == ExceptionAction.RERUN)
                // {
                // // Re-run query
                // continue;
                // }
                // }

                file.setExecutionState(ExecutionState.ERROR);
                if (System.getProperty("devEnv") != null)
                {
                    e.printStackTrace(fileView.getMessagesWriter());
                }
                else
                {
                    String message = e.getMessage();
                    if (e.getCause() != null)
                    {
                        message += " (" + e.getCause()
                                .getClass()
                                .getSimpleName() + ")";
                    }
                    fileView.getMessagesWriter()
                            .println(message);
                }
                // completed = true;
            }
            finally
            {
                writer.close();
                if (file.getExecutionState() == ExecutionState.EXECUTING)
                {
                    file.setExecutionState(ExecutionState.COMPLETED);
                }
                queryFinnishedCallback.run();
            }
            // }
        });
    }

    // /** Get named parameters from query. */
    // static Set<String> getVariables(String query)
    // {
    // QueryStatement parsedQuery;
    // try
    // {
    // parsedQuery = PARSER.parseQuery(query);
    // }
    // catch (Exception e)
    // {
    // // TODO: notify error parsing
    // return emptySet();
    // }
    // Set<String> parameters = new HashSet<>();
    // parsedQuery.getStatements()
    // .forEach(s -> s.accept(VARIABLES_VISITOR, parameters));
    // return parameters;
    // }
    //
    // /** Variable visitor. */
    // private static class VariableVisitor extends AStatementVisitor<Void, Set<String>>
    // {
    // private static final ExpressionVisitor EXPRESSION_VISITOR = new ExpressionVisitor();
    //
    // @Override
    // protected void visitExpression(Set<String> context, Expression expression)
    // {
    // expression.accept(EXPRESSION_VISITOR, context);
    // }
    //
    // /** Expression visitor. */
    // private static class ExpressionVisitor extends AExpressionVisitor<Void, Set<String>>
    // {
    // @Override
    // public Void visit(VariableExpression expression, Set<String> context)
    // {
    // context.add(expression.getName());
    // return null;
    // }
    // }
    // }
}
