package com.queryeer.api;

import java.util.Objects;

import com.queryeer.api.extensions.engine.IQueryEngine;

/** Query file meta with different information like description, sessionId's etc. */
public class QueryFileMetaData
{
    public static final QueryFileMetaData EMPTY = new QueryFileMetaData(null, null);

    /** Text that is shown in the status bar and on tab tooltip for this file. Can be used to specify which connection that is currently used etc */
    private final String description;
    /** An optional session id that is shown on file tab for {@link IQueryEngine}'s that supports that */
    private final String sessionId;

    public QueryFileMetaData(String description, String sessionId)
    {
        this.description = description;
        this.sessionId = sessionId;
    }

    public String getDescription()
    {
        return description;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(description, sessionId);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof QueryFileMetaData that)
        {
            return Objects.equals(description, that.description)
                    && Objects.equals(sessionId, that.sessionId);
        }
        return false;
    }
}