package com.queryeer.output.text;

import static java.util.Objects.requireNonNull;

import java.io.Writer;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.core.JsonOutputWriter;

/** Extension point for {@link JsonFormat} */
class JsonFormat implements IOutputFormatExtension
{
    static final String JSON = "JSON";
    private JsonConfigurable configurable;

    JsonFormat(JsonConfigurable configurable)
    {
        this.configurable = requireNonNull(configurable, "configurable");
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
    public OutputWriter createOutputWriter(IQueryFile file, Writer writer)
    {
        return new JsonTextOutputWriter(writer, file, configurable.getSettings());
    }

    @Override
    public IConfigurable getFileChooserConfigurableComponent()
    {
        return configurable;
    }

    /** Text Output writer for JSON */
    static class JsonTextOutputWriter extends JsonOutputWriter
    {
        private IQueryFile file;

        JsonTextOutputWriter(Writer writer, IQueryFile file, JsonSettings settings)
        {
            super(writer, settings);
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
