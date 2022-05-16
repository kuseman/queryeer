package com.queryeer.api.event;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.IQueryFile;

/** Base class for events where a {@link IQueryFile} is participating */
public abstract class QueryFileEvent extends Event
{
    protected final IQueryFile queryFile;

    public QueryFileEvent(IQueryFile queryFile)
    {
        this.queryFile = requireNonNull(queryFile, "queryFile");
    }

    public IQueryFile getQueryFile()
    {
        return queryFile;
    }
}