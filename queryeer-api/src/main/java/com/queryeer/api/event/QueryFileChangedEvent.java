package com.queryeer.api.event;

import com.queryeer.api.IQueryFile;

/** Event that is fired when the current selected {@link IQueryFile} was changed */
public class QueryFileChangedEvent extends QueryFileEvent
{
    public QueryFileChangedEvent(IQueryFile queryFile)
    {
        super(queryFile);
    }
}
