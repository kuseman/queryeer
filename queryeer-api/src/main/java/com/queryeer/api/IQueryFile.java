package com.queryeer.api;

import java.io.PrintWriter;

import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

import se.kuseman.payloadbuilder.api.execution.IQuerySession;

/** Definition of a query file. The editor tab with query text and results etc. */
public interface IQueryFile
{
    /** Return the query session associated with the query file */
    IQuerySession getSession();

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

    /** Selects output component with provided class. */
    void selectOutputComponent(Class<? extends IOutputComponent> outputComponentClass);

    /** Return the selected output component. */
    IOutputComponent getSelectedOutputComponent();

    /** Increment the row counter to let status etc. update */
    void incrementTotalRowCount();

    /** Get the current selected output format for this file */
    IOutputFormatExtension getOutputFormat();

    /** Clears the query files executions stats like runtime, number of rows etc. */
    void clearExecutionStats();

    /** Marks the provided selection in query window */
    void select(TextSelection textSelection);
}
