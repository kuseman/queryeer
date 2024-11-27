package com.queryeer.api.extensions.output.queryplan;

import com.queryeer.api.extensions.output.IOutputComponent;

/** Definition of query plan output component. */
public interface IQueryPlanOutputComponent extends IOutputComponent
{
    /** Adds a list of plans to the output component. */
    void addQueryPlan(Node plan);
}
