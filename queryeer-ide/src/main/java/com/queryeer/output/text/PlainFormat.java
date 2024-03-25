package com.queryeer.output.text;

import java.io.Writer;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.core.PlainTextOutputWriter;

/** Plain text format */
class PlainFormat implements IOutputFormatExtension, PlainTextOutputFormat
{
    @Override
    public String getTitle()
    {
        return "Plain";
    }

    @Override
    public OutputWriter createOutputWriter(IQueryFile file, Writer writer)
    {
        return new TextOutputWriter(writer, file);
    }

    static class TextOutputWriter extends PlainTextOutputWriter implements OutputWriter
    {
        private final IQueryFile file;
        private boolean header;

        TextOutputWriter(Writer writer, IQueryFile file)
        {
            super(writer);
            this.file = file;
        }

        @Override
        public void initResult(String[] columns)
        {
            header = true;
            super.initResult(columns);
            header = false;
        }

        @Override
        public void endRow()
        {
            super.endRow();
            if (!header)
            {
                file.incrementTotalRowCount();
            }
        }
    }
}
