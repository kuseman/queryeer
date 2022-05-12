package com.queryeer.output.none;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Extension point for {@link NoneOutput} */
class NoneOutput implements IOutputExtension
{
    @Override
    public String getTitle()
    {
        return "NONE";
    }

    @Override
    public int order()
    {
        return 30;
    }

    @Override
    public OutputWriter createOutputWriter(IQueryFile file)
    {
        return new NoneOutputWriter(file);
    }

    /** Output writer used in NONE output mode. */
    private static class NoneOutputWriter implements OutputWriter
    {
        private final IQueryFile file;
        private int rowCount;

        NoneOutputWriter(IQueryFile file)
        {
            this.file = file;
        }

        @Override
        public void close()
        {
            file.getMessagesWriter()
                    .println(String.valueOf(rowCount) + " row(s) selected" + System.lineSeparator());
        }

        @Override
        public void endRow()
        {
            file.incrementTotalRowCount();
            rowCount++;
        }

        @Override
        public void writeFieldName(String name)
        {
        }

        @Override
        public void writeValue(Object value)
        {
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
}
