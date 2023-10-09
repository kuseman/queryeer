package com.queryeer.api.extensions.payloadbuilder;

import com.queryeer.api.IQueryFile;

/**
 * Definition of a view for a catalog. Contains operations that Payloadbuilder query engine can call to indicate that query has exectued and state can be updated etc.
 */
public interface ICatalogExtensionView
{
    /** Called after a query is executed */
    void afterExecute(IQueryFile queryFile);

    /** Called when a query file got focus */
    void focus(IQueryFile queryFile);
}
