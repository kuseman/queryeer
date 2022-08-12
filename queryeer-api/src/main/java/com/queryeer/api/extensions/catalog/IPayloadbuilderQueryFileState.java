package com.queryeer.api.extensions.catalog;

import com.queryeer.api.IQueryFileState;

import se.kuseman.payloadbuilder.api.session.IQuerySession;

/** Query file state for a payloadbuilder query */
public interface IPayloadbuilderQueryFileState extends IQueryFileState
{
    /** Return the query files session */
    IQuerySession getSession();
}
