package com.queryeer;

import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;

import com.queryeer.QueryFileModel.State;
import com.queryeer.api.editor.TextSelection;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.QueryEngineException;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Query serivce. This is the core class that executes the current query and delegates to choosen {@link IQueryEngine} */
class QueryService
{
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BasicThreadFactory.Builder().daemon(true)
            .namingPattern("QueryExecutor-#%d")
            .build());

    /** Execute query for provider query file */
    static void executeQuery(final QueryFileView fileView, OutputWriter writer, Object query, boolean byEvent)
    {
        final QueryFileModel file = fileView.getModel();
        final IQueryEngine queryEngine = file.getQueryEngine();

        final ITextOutputComponent textOutput = fileView.getOutputComponent(ITextOutputComponent.class);
        EXECUTOR.execute(() ->
        {
            file.setState(byEvent ? State.EXECUTING_BY_EVENT
                    : State.EXECUTING);

            try
            {
                queryEngine.execute(fileView, writer, query);
            }
            catch (Exception e)
            {
                file.setState(State.ERROR);

                boolean errorHandled = false;
                if (e instanceof QueryEngineException qee)
                {
                    errorHandled = qee.isErrorHandled();
                }

                if (!errorHandled)
                {
                    Throwable t = ExceptionUtils.getRootCause(e);
                    if (t == null)
                    {
                        t = e;
                    }

                    String message = t.getMessage();
                    textOutput.appendWarning(message, TextSelection.EMPTY);

                    if (System.getProperty("devEnv") != null)
                    {
                        e.printStackTrace();
                    }
                }
            }
            finally
            {
                writer.close();
                if (file.getState()
                        .isExecuting())
                {
                    file.setState(State.COMPLETED);
                }

                textOutput.getTextWriter()
                        .println("Finished: " + ZonedDateTime.now()
                                .toString());
            }
        });
    }
}