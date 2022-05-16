package com.queryeer.api.event;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.IQueryFile;

/** Event that is fired when the state of a query file is changed */
public class QueryFileStateEvent extends QueryFileEvent
{
    private final State state;

    public QueryFileStateEvent(IQueryFile queryFile, State state)
    {
        super(queryFile);
        this.state = requireNonNull(state, "state");
    }

    public State getState()
    {
        return state;
    }

    /** States of query file */
    public enum State
    {
        BEFORE_QUERY_EXECUTE,
        AFTER_QUERY_EXECUTE
    }

}
