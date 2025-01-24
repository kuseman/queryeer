package com.queryeer.api;

import java.io.PrintWriter;

import com.queryeer.api.editor.IEditor;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

/** Definition of a query file. The editor tab with query text and results etc. */
public interface IQueryFile
{
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

    /** Add a new output component to query file. */
    void addOutputComponent(IOutputComponent outputComponent);

    /** Return the selected output component. */
    IOutputComponent getSelectedOutputComponent();

    /** Increment the row counter to let status etc. update */
    void incrementTotalRowCount();

    /** Get the current selected output format for this file */
    IOutputFormatExtension getOutputFormat();

    /** Return the query files editor */
    IEditor getEditor();

    /** Set status on file */
    void setMetaData(QueryFileMetaData status);

    /**
     * Get engine state from this file
     */
    <T extends IQueryEngine.IState> T getEngineState();
}
