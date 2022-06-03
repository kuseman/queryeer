package com.queryeer.api;

import java.io.PrintWriter;
import java.util.Optional;

import com.queryeer.api.extensions.IQueryProvider;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

/** Definition of a query file. The editor tab with query text and results etc. */
public interface IQueryFile
{
    /**
     * Get the query state. This is the state that is created/updated by the files {@link IQueryProvider}
     *
     * @return Returns optional with the state. Will be an empty if the provided class is not of the provided state class
     */
    <T extends IQueryFileState> Optional<T> getQueryFileState(Class<T> clazz);

    /** Get the query files execution state */
    ExecutionState getExecutionState();

    /** Set the files execution state */
    void setExecutionState(ExecutionState error);

    /** Get filename of this query file */
    String getFilename();

    /** Set focus the messages output component */
    void focusMessages();

    /** Get the messages print writer for this file */
    PrintWriter getMessagesWriter();

    /**
     * Get the output component of the provided class.
     *
     * @param <T> Type of output component
     * @return Return the output component or null of no such component could be found
     */
    <T extends IOutputComponent> T getOutputComponent(Class<T> clazz);

    /** Increment the row counter to let status etc. update */
    void incrementTotalRowCount();

    /** Get the current selected output extension for this file */
    IOutputExtension getOutput();

    /** Get the current selected output format for this file */
    IOutputFormatExtension getOutputFormat();

    /** Clears the query files executions stats like runtime, number of rows etc. */
    void clearExecutionStats();

    /** Execution state of the query file */
    enum ExecutionState
    {
        COMPLETED,
        EXECUTING,
        ABORTED,
        ERROR;
    }
}
