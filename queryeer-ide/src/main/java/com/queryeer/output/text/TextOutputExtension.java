package com.queryeer.output.text;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Extension point for {@link TextOutputExtension} */
class TextOutputExtension implements IOutputExtension
{
    static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    static final String NAME = "com.queryeer.output.text.TextOutputExtension";

    @Override
    public String getTitle()
    {
        return "TEXT";
    }

    @Override
    public int order()
    {
        return 20;
    }

    @Override
    public boolean supportsOutputFormats()
    {
        return true;
    }

    @Override
    public KeyStroke getKeyStroke()
    {
        return KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK);
    }

    @Override
    public IOutputComponent createResultComponent()
    {
        return new TextOutputComponent();
    }

    @Override
    public Class<? extends IOutputComponent> getResultOutputComponentClass()
    {
        return TextOutputComponent.class;
    }

    @Override
    public OutputWriter createOutputWriter(IQueryFile file)
    {
        IOutputFormatExtension outputFormat = file.getOutputFormat();
        if (outputFormat == null)
        {
            throw new IllegalArgumentException("No output format selected");
        }

        return outputFormat.createOutputWriter(file, file.getMessagesWriter());
    }
}
