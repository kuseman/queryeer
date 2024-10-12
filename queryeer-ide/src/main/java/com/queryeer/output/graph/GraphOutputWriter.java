package com.queryeer.output.graph;

import java.util.ArrayList;
import java.util.List;

import com.queryeer.api.IQueryFile;

import se.kuseman.payloadbuilder.api.OutputWriter;

class GraphOutputWriter implements OutputWriter
{
    private final IQueryFile queryFile;
    private final GraphOutputComponent outputComponent;
    private final List<Object> rowValues = new ArrayList<>();
    private final List<String> rowColumns = new ArrayList<>();

    private String field;
    private int rowNumber;

    GraphOutputWriter(IQueryFile queryFile)
    {
        this.queryFile = queryFile;
        outputComponent = this.queryFile.getOutputComponent(GraphOutputComponent.class);
    }

    @Override
    public void startRow()
    {
        rowColumns.clear();
        rowValues.clear();
    }

    @Override
    public void endRow()
    {
        rowNumber++;
        outputComponent.addRow(rowNumber, rowColumns, rowValues);
    }

    @Override
    public void endResult()
    {
        outputComponent.endResult();
    }

    @Override
    public void writeFieldName(String name)
    {
        this.field = name;
    }

    @Override
    public void writeValue(Object value)
    {
        if (field == null)
        {
            return;
        }

        rowColumns.add(field);
        rowValues.add(value);
        field = null;
    }

    @Override
    public void startObject()
    {
    }

    @Override
    public void endObject()
    {
    }

    @Override
    public void startArray()
    {
    }

    @Override
    public void endArray()
    {
    }
}
