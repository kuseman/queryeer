package com.queryeer.event;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.event.Event;

/** Event fired when a query file is about to be closed */
public class QueryFileClosingEvent extends Event
{
    private final IQueryFile queryFile;

    public QueryFileClosingEvent(IQueryFile queryFile)
    {
        this.queryFile = queryFile;
    }

    public IQueryFile getQueryFile()
    {
        return queryFile;
    }
}
