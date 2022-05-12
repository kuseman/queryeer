package com.queryeer.output.table;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** The main table output extension */
class TableOutput implements IOutputExtension
{
    @Override
    public String getTitle()
    {
        return "TABLE";
    }

    @Override
    public int order()
    {
        return 10;
    }

    @Override
    public KeyStroke getKeyStroke()
    {
        return KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK);
    }

    @Override
    public IOutputComponent createResultComponent(IQueryFile file)
    {
        return new TableOutputComponent();
    }

    @Override
    public OutputWriter createOutputWriter(IQueryFile file)
    {
        return new TableOutputWriter(file);
    }
}
