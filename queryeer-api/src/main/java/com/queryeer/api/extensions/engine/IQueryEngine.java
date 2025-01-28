package com.queryeer.api.extensions.engine;

import static java.util.Collections.emptyList;

import java.awt.Component;
import java.io.Closeable;
import java.util.List;

import javax.swing.Icon;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.DialogUtils.IQuickSearchModel;
import com.queryeer.api.editor.IEditor;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
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
     * @param writer Writer that should be used for the result. {@see QueryeerOutputWriter}
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

    /**
     * Create a {@link ExecuteQueryEvent} for a query and output type that should be executed in the current query file context.
     */
    default ExecuteQueryEvent getExecuteQueryEvent(String query, String newQueryName, OutputType outputType)
    {
        return null;
    }

    /**
     * Return a search model used for switching data sources on current query file via quick search.
     *
     * @return Model for handling switching of datasource or null if not supported
     */
    default <T extends IQuickSearchModel.Item> IQuickSearchModel<T> getDatasourceSearchModel()
    {
        return null;
    }

    /** State that can be stored on a query file. Engines can use this to store a connection etc. that is associated with the file */
    interface IState extends Closeable
    {
        /** Return the owning query engine for this state. */
        IQueryEngine getQueryEngine();

        /**
         * Return list of meta parameters for this state. These parameters can be used in different places for evaluating rules etc. depending on context. Query actions/shortcuts etc.
         *
         * @param testData If true then this call is from UI where we don't have a real state and test data should be returned otherwise false
         */
        default List<MetaParameter> getMetaParameters(boolean testData)
        {
            return emptyList();
        }

        /** A meta parameter. Key/value plus description. */
        record MetaParameter(String name, Object value, String description, List<MetaParameter> subParameters)
        {
            public MetaParameter(String name, Object value, String description)
            {
                this(name, value, description, emptyList());
            }
        }
    }
}
