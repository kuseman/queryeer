package com.queryeer.api.service;

import com.queryeer.api.IQueryFile;

/** Query file provider. Gets access to the current selected {@link IQueryFile} */
public interface IQueryFileProvider
{
    /** Returns the current open query file */
    IQueryFile getCurrentFile();
}
