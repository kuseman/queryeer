package com.queryeer.output.table;

import static java.util.Objects.requireNonNull;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.KeyStroke;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.table.ITableContextMenuActionFactory;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** The main table output extension */
class TableOutputExtension implements IOutputExtension
{
    private List<ITableContextMenuActionFactory> contextMenuActionFactories;

    TableOutputExtension(List<ITableContextMenuActionFactory> contextMenuActionFactories)
    {
        this.contextMenuActionFactories = requireNonNull(contextMenuActionFactories, "contextMenuActionFactories");
    }

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
    public IOutputComponent createResultComponent()
    {
        return new TableOutputComponent(contextMenuActionFactories);
    }

    @Override
    public Class<? extends IOutputComponent> getResultOutputComponentClass()
    {
        return TableOutputComponent.class;
    }

    @Override
    public OutputWriter createOutputWriter(IQueryFile file)
    {
        return new TableOutputWriter(file);
    }
}
