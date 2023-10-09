package com.queryeer.api.extensions.payloadbuilder;

import com.queryeer.api.extensions.engine.IQueryEngine;

import se.kuseman.payloadbuilder.api.execution.IQuerySession;

/** Definition of state used by payloadbuilder query engine. Containing state for execution */
public interface IPayloadbuilderState extends IQueryEngine.IState
{
    /** Return query session for this state */
    IQuerySession getQuerySession();
}
