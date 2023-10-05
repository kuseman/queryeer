package com.queryeer;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Color;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import com.queryeer.QueryFileModel.State;
import com.queryeer.api.TextSelection;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtension.ExceptionAction;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;
import com.queryeer.domain.ICatalogModel;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.core.CompiledQuery;
import se.kuseman.payloadbuilder.core.Payloadbuilder;
import se.kuseman.payloadbuilder.core.QueryResult;
import se.kuseman.payloadbuilder.core.parser.ParseException;

/** Class the executes queries etc. */
class PayloadbuilderService
{
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

                ITextOutputComponent textOutput = fileView.getOutputComponent(ITextOutputComponent.class);
                try
                {
                    file.getQuerySession()
                            .setAbortSupplier(() -> file.getState() == State.ABORTED);

                    file.getQuerySession()
                            .setExceptionHandler(e -> textOutput.appendWarning(e.getMessage(), TextSelection.EMPTY));

                    CompiledQuery query = Payloadbuilder.compile(file.getQuerySession(), queryString);

                    if (!query.getWarnings()
                            .isEmpty())
                    {
                        for (CompiledQuery.Warning warning : query.getWarnings())
                        {
                            TextSelection selection = fileView.getSelection(warning.location());
                            String message = "Warning, line: " + warning.location()
                                    .line() + System.lineSeparator() + warning.message() + System.lineSeparator();
                            textOutput.appendWarning(message, selection);
                            fileView.highlight(selection, Color.BLUE);
                        }

                        textOutput.getTextWriter()
                                .append(System.lineSeparator());
                    }

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

                        long time = file.getQuerySession()
                                .getLastQueryExecutionTime();
                        long rowCount = file.getQuerySession()
                                .getLastQueryRowCount();

                        String output = String.format("%s%d row(s) affected, execution time: %s", System.lineSeparator(), rowCount, DurationFormatUtils.formatDurationHMS(time));
                        file.getQuerySession()
                                .printLine(output);
                    }
                    completed = true;
                }
                catch (ParseException e)
                {
                    TextSelection selection = fileView.getSelection(e.getLocation());
                    String message = "Syntax error, line: " + e.getLocation()
                            .line() + System.lineSeparator() + e.getMessage() + System.lineSeparator();
                    fileView.highlight(selection, Color.RED);
                    textOutput.appendWarning(message, selection);
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

                    Throwable t = ExceptionUtils.getRootCause(e);
                    if (t == null)
                    {
                        t = e;
                    }

                    String message = t.getMessage();
                    textOutput.appendWarning(message, TextSelection.EMPTY);

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
}
