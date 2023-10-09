package com.queryeer.api.extensions.engine;

import java.awt.Component;
import java.io.Closeable;

import javax.swing.Icon;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.extensions.IExtension;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;

import se.kuseman.payloadbuilder.api.OutputWriter;

/**
 * Definition of a query engine. This is the extension for providing a catalog native query engine
 */
public interface IQueryEngine extends IExtension
{
    /** Create an editor for provided filename and state. */
    IEditor createEditor(IQueryEngine.IState state, String filename);

    /** Create new engine state for this query engine */
    default IQueryEngine.IState createState()
    {
        return null;
    }

    /** Initalize a query file upon creation/loading. Can be used to set up state, adjust UI etc. */
    default void focus(IQueryFile queryFile)
    {
    }

    /**
     * Clone the provided query files state. This is called when a new query file is opened and provided query file is the currently opened one
     */
    default IState cloneState(IQueryFile queryFile)
    {
        return null;
    }

    /**
     * Method called before {@link #execute(IQueryFile, OutputWriter, ITextOutputComponent, String)} to let query engines perform and do validations etc. before actually doing a query
     */
    default boolean shouldExecute(IQueryFile queryFile)
    {
        return true;
    }

    /**
     * Execute query NOTE! Is executed in a threaded fashion
     *
     * @param queryFile The query file "owning" the execution
     * @param writer Writer that should be used for the result
     * @param query Query to execute. This is the current {@link IQueryFile}'s {@link IEditor}'s value. NOTE! This value is not necessarily a String but can also be a custom query object sent by
     * {@link ExecuteQueryEvent}..
     */
    void execute(IQueryFile queryFile, OutputWriter writer, Object query) throws Exception;

    /** Abort running query */
    void abortQuery(IQueryFile queryFile);

    /**
     * Get quick properties component. Will be showed in extensions side bar with quick properties. Ie. selected. database/index.
     */
    Component getQuickPropertiesComponent();

    /** Return title of query engine */
    String getTitle();

    /** Return the default file extension for this query engine */
    String getDefaultFileExtension();

    /** Get icon for this query engine. Is shown in various places to mark which engine a query file belongs to etc. */
    default Icon getIcon()
    {
        return null;
    }

    /** Return the order that this engine appears in UI */
    default int order()
    {
        return 0;
    }

    /** State that can be stored on a query file. Engines can use this to store a connection etc. that is associated with the file */
    interface IState extends Closeable
    {
    }
}