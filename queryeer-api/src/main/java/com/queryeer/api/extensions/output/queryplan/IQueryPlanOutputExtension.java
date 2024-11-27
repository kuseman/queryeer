package com.queryeer.api.extensions.output.queryplan;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Definition of query plan output extension. */
public interface IQueryPlanOutputExtension extends IOutputExtension
{
    @Override
    default OutputWriter createOutputWriter(IQueryFile file)
    {
        throw new IllegalArgumentException("This output extension does not support output writers");
    }
}
