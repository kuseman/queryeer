package com.queryeer.api.extensions.engine;

/** Exception that can be thrown by {@link IQueryEngine}'s to handle outcome of query etc. */
public class QueryEngineException extends RuntimeException
{
    /** Flag that indicates that the actual error has been handled and this exception is only a marker that the query did not execute successfully */
    private final boolean errorHandled;

    public QueryEngineException(String message, boolean errorHandled)
    {
        this.errorHandled = errorHandled;
    }

    public boolean isErrorHandled()
    {
        return errorHandled;
    }
}
