package com.queryeer.payloadbuilder;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.function.BooleanSupplier;

import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.payloadbuilder.IPayloadbuilderState;

import se.kuseman.payloadbuilder.core.execution.QuerySession;

/** State for payloadbuilder */
class PayloadbuilderState implements IPayloadbuilderState, BooleanSupplier
{
    private final IQueryEngine queryEngine;
    private final QuerySession querySession;
    volatile boolean abort;

    PayloadbuilderState(IQueryEngine queryEngine, QuerySession querySession)
    {
        this.queryEngine = requireNonNull(queryEngine, "queryEngine");
        this.querySession = requireNonNull(querySession, "querySession");
    }

    void abort()
    {
        querySession.fireAbortQueryListeners();
        abort = true;
    }

    @Override
    public QuerySession getQuerySession()
    {
        return querySession;
    }

    @Override
    public void close() throws IOException
    {
    }

    @Override
    public IQueryEngine getQueryEngine()
    {
        return queryEngine;
    }

    @Override
    public boolean getAsBoolean()
    {
        return abort;
    }
}