package com.queryeer.output.text;

import static java.util.Objects.requireNonNull;

import java.io.Writer;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.core.CsvOutputWriter;

/** Extension point for {@link CsvFormat} */
class CsvFormat implements IOutputFormatExtension
{
    static final String CSV = "CSV";
    private CsvConfigurable configurable;

    CsvFormat(CsvConfigurable configurable)
    {
        this.configurable = requireNonNull(configurable, "configurable");
    }

    @Override
    public String getTitle()
    {
        return CSV;
    }

    @Override
    public int order()
    {
        return 10;
    }

    @Override
    public OutputWriter createOutputWriter(IQueryFile file, Writer writer)
    {
        return new CsvTextOutputWriter(writer, file, configurable.getSettings());
    }

    /** CSV Output writer */
    static class CsvTextOutputWriter extends CsvOutputWriter
    {
        private final IQueryFile file;
        private boolean header;

        CsvTextOutputWriter(Writer writer, IQueryFile file, CsvSettings settings)
        {
            super(writer, settings);
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
