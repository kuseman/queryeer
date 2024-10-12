package com.queryeer.output.graph;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Graphing output extension */
class GraphOutputExtension implements IOutputExtension
{
    @Override
    public String getTitle()
    {
        return null;
    }

    @Override
    public int order()
    {
        return 100;
    }

    @Override
    public boolean isAutoPopulated()
    {
        return true;
    }

    @Override
    public IOutputComponent createResultComponent(IQueryFile queryFile)
    {
        return new GraphOutputComponent(this, queryFile);
    }

    @Override
    public OutputWriter createOutputWriter(IQueryFile file)
    {
        return new GraphOutputWriter(file);
    }
}
