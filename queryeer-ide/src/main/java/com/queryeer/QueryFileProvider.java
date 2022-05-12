package com.queryeer;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.service.IQueryFileProvider;

/** Query file provider */
class QueryFileProvider implements IQueryFileProvider
{
    private IQueryFile queryFile;

    @Override
    public IQueryFile getCurrentFile()
    {
        return queryFile;
    }

    void setQueryFile(IQueryFile queryFile)
    {
        this.queryFile = queryFile;
    }
}
