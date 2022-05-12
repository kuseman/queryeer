package com.queryeer.output.table;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.event.TableModelEvent;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.Constants;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;

/** The main panel that contains all the result set tables */
class TableOutputComponent extends JPanel implements IOutputComponent
{
    static final int COLUMN_ADJUST_ROW_LIMIT = 30;
    private final List<Table> tables = new ArrayList<>();

    TableOutputComponent()
    {
        setLayout(new BorderLayout());
    }

    @Override
    public String title()
    {
        return "Results";
    }

    @Override
    public Icon icon()
    {
        return FontIcon.of(FontAwesome.TABLE);
    }

    @Override
    public Component getComponent()
    {
        return this;
    }

    @Override
    public Class<? extends IOutputExtension> getExtensionClass()
    {
        return TableOutput.class;
    }

    @Override
    public void clearState()
    {
        removeAll();
        tables.clear();
    }

    /** Add new result set to this instance */
    void addResult(final Model model)
    {
        final Table resultTable = new Table();
        model.addTableModelListener(e ->
        {
            if (!resultTable.columnsAdjusted.get()
                    && e.getType() == TableModelEvent.INSERT
                    && model.getRowCount() > COLUMN_ADJUST_ROW_LIMIT)
            {
                resultTable.adjustColumns();
            }
            else if (e.getFirstRow() == TableModelEvent.HEADER_ROW
                    && resultTable.columnsAdjusted.get())
            {
                resultTable.restoreWidths();
            }
        });
        resultTable.setModel(model);

        if (tables.size() > 0)
        {
            resizeLastTablesColumns();
        }
        tables.add(resultTable);
        int size = tables.size();
        removeAll();

        // 8 rows plus header plus spacing
        int tablHeight = resultTable.getRowHeight() * 9 + 10;

        Component parent = null;

        for (int i = 0; i < size; i++)
        {
            JTable table = tables.get(i);
            JTable prevTable = i > 0 ? tables.get(i - 1)
                    : null;
            int prevTableHeight = -1;
            if (i > 0)
            {
                JScrollBar horizontalScrollBar = ((JScrollPane) ((JViewport) prevTable.getParent()).getParent()).getHorizontalScrollBar();

                // The least of 8 rows or actual rows in prev table
                // CSOFF
                prevTableHeight = Math.min((prevTable.getRowCount() + 1) * prevTable.getRowHeight() + 15, tablHeight);
                // CSON
                if (horizontalScrollBar.isVisible())
                {
                    prevTableHeight += Constants.SCROLLBAR_WIDTH;
                }
            }
            // Single table
            if (i == 0)
            {
                parent = table;
            }
            // Split panel
            else if (i == 1)
            {
                JSplitPane sp = new JSplitPane();
                sp.setOrientation(JSplitPane.VERTICAL_SPLIT);
                sp.setLeftComponent(new JScrollPane(parent));
                // Adjust prev tables height
                sp.getLeftComponent()
                        .setPreferredSize(new Dimension(0, prevTableHeight));
                sp.setRightComponent(new JScrollPane(table));
                sp.getRightComponent()
                        .setPreferredSize(new Dimension(0, tablHeight));

                parent = sp;
            }
            // Nested split panel
            else
            {
                JSplitPane prevSp = (JSplitPane) parent;
                Component rc = prevSp.getRightComponent();

                JSplitPane sp = new JSplitPane();
                sp.setOrientation(JSplitPane.VERTICAL_SPLIT);

                // Adjust prev tables height
                if (rc instanceof JScrollPane)
                {
                    sp.setLeftComponent(new JScrollPane(prevTable));
                    sp.getLeftComponent()
                            .setPreferredSize(new Dimension(0, prevTableHeight));
                }
                else if (rc instanceof JSplitPane)
                {
                    ((JSplitPane) rc).getRightComponent()
                            .setPreferredSize(new Dimension(0, prevTableHeight));
                    sp.setLeftComponent(rc);
                }
                sp.setRightComponent(new JScrollPane(table));
                sp.getRightComponent()
                        .setPreferredSize(new Dimension(0, tablHeight));

                JSplitPane topSp = new JSplitPane();
                topSp.setOrientation(JSplitPane.VERTICAL_SPLIT);

                // Replace the right component with the new split panel
                prevSp.setRightComponent(sp);
            }
        }
        add(new JScrollPane(parent), BorderLayout.CENTER);
    }

    void resizeLastTablesColumns()
    {
        // Resize last columns if not already done
        if (tables.size() > 0
                && !tables.get(tables.size() - 1).columnsAdjusted.get())
        {
            Table lastTable = tables.get(tables.size() - 1);
            lastTable.adjustColumns();
        }
    }

    int getTotalRowCount()
    {
        return tables.stream()
                .mapToInt(JTable::getRowCount)
                .sum();
    }
}
