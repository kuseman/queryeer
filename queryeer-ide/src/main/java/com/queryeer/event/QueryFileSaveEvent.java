package com.queryeer.event;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.event.Event;

/** Event fired when a query file should be saved. */
public class QueryFileSaveEvent extends Event
{
    private final IQueryFile queryFile;
    private boolean canceled;

    public QueryFileSaveEvent(IQueryFile queryFile)
    {
        this.queryFile = queryFile;
    }

    public IQueryFile getQueryFile()
    {
        return queryFile;
    }

    public void setCanceled(boolean canceled)
    {
        this.canceled = canceled;
    }

    public boolean isCanceled()
    {
        return canceled;
    }
}
