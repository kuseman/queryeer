package com.queryeer;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.QueryFileModel.State;
import com.queryeer.api.editor.TextSelection;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.QueryEngineException;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Query serivce. This is the core class that executes the current query and delegates to choosen {@link IQueryEngine} */
class QueryService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryService.class);
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BasicThreadFactory.Builder().daemon(true)
            .namingPattern("QueryExecutor-#%d")
            .build());

    /** Execute query for provider query file */
    static void executeQuery(final QueryFileModel file, OutputWriter writer, Object query, boolean byEvent, Runnable afterQuery)
    {
        final IQueryEngine queryEngine = file.getQueryEngine();

        final ITextOutputComponent textOutput = file.getOutputComponent(ITextOutputComponent.class);
        file.setState(byEvent ? State.EXECUTING_BY_EVENT
                : State.EXECUTING);
        EXECUTOR.execute(() ->
        {
            LOGGER.debug("Execute. File: {}, ByEvent: {}, Query: {}", file.getFile(), byEvent, query);

            OutputWriter theWriter = writer;
            if (LOGGER.isDebugEnabled())
            {
                theWriter = new ProxyOutputWriter(List.of(theWriter))
                {
                    int resultCount = 0;
                    int rowCount = 0;

                    @Override
                    public void initResult(String[] columns)
                    {
                        rowCount = 0;
                        LOGGER.debug("Init result: {}, columns: {}", ++resultCount, columns);
                        super.initResult(columns);
                    }

                    @Override
                    public void endRow()
                    {
                        rowCount++;
                        super.endRow();
                    }

                    @Override
                    public void endResult()
                    {
                        LOGGER.debug("End result: {}, rowCount: {}", resultCount, rowCount);
                        super.endResult();
                    }
                };
            }

            try
            {
                queryEngine.execute(file, theWriter, query);
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
                    LOGGER.error("Unhandled query error", e);
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
                textOutput.getTextWriter()
                        .flush();

                afterQuery.run();
            }
        });
    }
}
