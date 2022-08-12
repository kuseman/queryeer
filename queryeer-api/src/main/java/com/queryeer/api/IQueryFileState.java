package com.queryeer.api;

import java.beans.PropertyChangeListener;

import com.queryeer.api.component.IQueryEditorComponent;
import com.queryeer.api.extensions.IQueryProvider;

/** Base interface for query file state. This is the state that each query file has */
public interface IQueryFileState
{
    public static final String DIRTY = "dirty";

    /** Get the query provider that this state belongs to */
    IQueryProvider getQueryProvider();

    /** Get the query editor component for this state */
    IQueryEditorComponent getQueryEditorComponent();

    /** Load state from persistence */
    void load(String filename) throws Exception;

    /** Save state to provided filename */
    void save(String filename) throws Exception;

    /** Get summary of the same. This value is shown for example in the tab title of the owning query file */
    default String getSummary()
    {
        return "";
    }

    /** Initializes the state with a new {@link IQueryFile} */
    default void init(IQueryFile file)
    {
    }

    /** Closes thi state. Called to be able to clean up resources etc. */
    void close();

    /**
     * Clone this state. Is used when a new query is added to Queryeer and let providers be able to copy state from the previous opened query to select the same data sources etc.
     */
    default IQueryFileState cloneState()
    {
        return null;
    }

    /** Add property change listener */
    void addPropertyChangeListener(PropertyChangeListener propertyChangeListener);

    /** Remove property change listener */
    void removePropertyChangeListener(PropertyChangeListener propertyChangeListener);
}
