package com.queryeer.output.queryplan;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputExtension;

/** Output extension for query plans. */
class QueryPlanOutputExtension implements IQueryPlanOutputExtension
{
    @Override
    public String getTitle()
    {
        return "Query Plan";
    }

    @Override
    public IOutputComponent createResultComponent(IQueryFile file)
    {
        return new QueryPlanOutputComponent(this);
    }
}
