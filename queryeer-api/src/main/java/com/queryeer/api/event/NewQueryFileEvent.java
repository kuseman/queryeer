package com.queryeer.api.event;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.IQueryEngine.IState;

/** Event that can be fired to open up a new query file */
public class NewQueryFileEvent extends Event
{
    /** Engine for this new file */
    private final IQueryEngine queryEngine;

    /** Pre created state of new file */
    private final IState state;

    /** Editor content of new file */
    private final Object editorValue;

    public NewQueryFileEvent(IQueryEngine queryEngine, IQueryEngine.IState state, Object editorValue)
    {
        this.queryEngine = requireNonNull(queryEngine);
        this.state = state;
        this.editorValue = editorValue;
    }

    public IQueryEngine getQueryEngine()
    {
        return queryEngine;
    }

    public IState getState()
    {
        return state;
    }

    public Object getEditorValue()
    {
        return editorValue;
    }
}
