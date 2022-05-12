package com.queryeer.output.text;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.core.JsonOutputWriter;
import se.kuseman.payloadbuilder.core.JsonOutputWriter.JsonSettings;

/** Extension point for {@link JsonFormat} */
class JsonFormat implements IOutputFormatExtension
{
    static final String JSON = "JSON";
    private JsonSettings settings;

    JsonFormat(JsonConfigurable configurable)
    {
        this.settings = requireNonNull(configurable, "configurable").getSettings();
    }

    @Override
    public String getTitle()
    {
        return JSON;
    }

    @Override
    public int order()
    {
        return 20;
    }

    @Override
    public OutputWriter createOutputWriter(IQueryFile file)
    {
        return new JsonTextOutputWriter(file, settings);
    }

    /** Text Output writer for JSON */
    static class JsonTextOutputWriter extends JsonOutputWriter
    {
        private IQueryFile file;

        JsonTextOutputWriter(IQueryFile file, JsonSettings settings)
        {
            super(file.getMessagesWriter(), settings);
            this.file = file;
        }

        @Override
        public void endRow()
        {
            super.endRow();
            file.incrementTotalRowCount();
        }
    }
}
