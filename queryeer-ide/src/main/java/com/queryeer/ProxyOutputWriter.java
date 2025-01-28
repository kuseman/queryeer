package com.queryeer;

import static java.util.Objects.requireNonNull;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.queryeer.api.extensions.output.QueryeerOutputWriter;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.api.execution.Decimal;
import se.kuseman.payloadbuilder.api.execution.EpochDateTime;
import se.kuseman.payloadbuilder.api.execution.EpochDateTimeOffset;
import se.kuseman.payloadbuilder.api.execution.UTF8String;

class ProxyOutputWriter implements QueryeerOutputWriter
{
    private List<OutputWriter> writers;

    ProxyOutputWriter(List<OutputWriter> writers)
    {
        this.writers = requireNonNull(writers, "writers");
    }

    @Override
    public void flush()
    {
        writers.forEach(w -> w.flush());
    }

    @Override
    public void close()
    {
        writers.forEach(w -> w.close());
    }

    @Override
    public void initResult(String[] columns, Map<String, Object> resultMetaData)
    {
        if (!resultMetaData.containsKey(METADATA_TIMESTAMP))
        {
            resultMetaData = new LinkedHashMap<>(resultMetaData);
            resultMetaData.put(METADATA_TIMESTAMP, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()));
        }

        final Map<String, Object> metaMap = resultMetaData;
        writers.forEach(w ->
        {
            if (w instanceof QueryeerOutputWriter qw)
            {
                qw.initResult(columns, metaMap);
            }
            else
            {
                w.initResult(columns);
            }
        });
    }

    @Override
    public void initResult(String[] columns)
    {
        writers.forEach(w -> w.initResult(columns));
    }

    @Override
    public void endResult()
    {
        writers.forEach(w -> w.endResult());
    }

    @Override
    public void startRow()
    {
        writers.forEach(w -> w.startRow());
    }

    @Override
    public void endRow()
    {
        writers.forEach(w -> w.endRow());
    }

    @Override
    public void writeFieldName(String name)
    {
        writers.forEach(w -> w.writeFieldName(name));
    }

    @Override
    public void writeValue(Object value)
    {
        writers.forEach(w -> w.writeValue(value));
    }

    @Override
    public void startObject()
    {
        writers.forEach(w -> w.startObject());
    }

    @Override
    public void endObject()
    {
        writers.forEach(w -> w.endObject());
    }

    @Override
    public void startArray()
    {
        writers.forEach(w -> w.startArray());
    }

    @Override
    public void endArray()
    {
        writers.forEach(w -> w.endArray());
    }

    @Override
    public void writeInt(int value)
    {
        writers.forEach(w -> w.writeInt(value));
    }

    @Override
    public void writeLong(long value)
    {
        writers.forEach(w -> w.writeLong(value));
    }

    @Override
    public void writeFloat(float value)
    {
        writers.forEach(w -> w.writeFloat(value));
    }

    @Override
    public void writeDouble(double value)
    {
        writers.forEach(w -> w.writeDouble(value));
    }

    @Override
    public void writeBool(boolean value)
    {
        writers.forEach(w -> w.writeBool(value));
    }

    @Override
    public void writeString(UTF8String string)
    {
        writers.forEach(w -> w.writeString(string));
    }

    @Override
    public void writeNull()
    {
        writers.forEach(w -> w.writeNull());
    }

    @Override
    public void writeDecimal(Decimal decimal)
    {
        writers.forEach(w -> w.writeDecimal(decimal));
    }

    @Override
    public void writeDateTime(EpochDateTime datetime)
    {
        writers.forEach(w -> w.writeDateTime(datetime));
    }

    @Override
    public void writeDateTimeOffset(EpochDateTimeOffset datetimeOffset)
    {
        writers.forEach(w -> w.writeDateTimeOffset(datetimeOffset));
    }
}
