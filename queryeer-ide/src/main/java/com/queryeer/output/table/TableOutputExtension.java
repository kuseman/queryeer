package com.queryeer.output.table;

import static java.util.Objects.requireNonNull;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.KeyStroke;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.IDialogFactory;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.table.ITableContextMenuActionFactory;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** The main table output extension */
class TableOutputExtension implements IOutputExtension
{
    private final List<ITableContextMenuActionFactory> contextMenuActionFactories;
    private final TableActionsConfigurable tableActionsConfigurable;
    private final IDialogFactory dialogFactory;

    TableOutputExtension(List<ITableContextMenuActionFactory> contextMenuActionFactories, TableActionsConfigurable tableActionsConfigurable, IDialogFactory dialogFactory)
    {
        this.contextMenuActionFactories = requireNonNull(contextMenuActionFactories, "contextMenuActionFactories");
        this.tableActionsConfigurable = requireNonNull(tableActionsConfigurable, "tableActionsConfigurable");
        this.dialogFactory = requireNonNull(dialogFactory, "dialogFactory");
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
        return KeyStroke.getKeyStroke(KeyEvent.VK_T, Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx());
    }

    @Override
    public IOutputComponent createResultComponent(IQueryFile queryFile)
    {
        return new TableOutputComponent(queryFile, this, contextMenuActionFactories, tableActionsConfigurable, dialogFactory);
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
