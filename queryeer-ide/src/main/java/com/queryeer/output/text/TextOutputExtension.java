package com.queryeer.output.text;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputFormatExtension;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;

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
        return KeyStroke.getKeyStroke(KeyEvent.VK_T, Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx() + InputEvent.SHIFT_DOWN_MASK);
    }

    @Override
    public IOutputComponent createResultComponent(IQueryFile queryFile)
    {
        return new TextOutputComponent(this, queryFile);
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

        TextOutputComponent outputComponent = (TextOutputComponent) file.getOutputComponent(ITextOutputComponent.class);
        return outputFormat.createOutputWriter(file, outputComponent.getTextWriter());
    }
}
