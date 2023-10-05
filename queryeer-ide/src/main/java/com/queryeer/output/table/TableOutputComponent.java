package com.queryeer.output.table;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;

import org.apache.commons.lang3.StringUtils;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rtextarea.SearchContext;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.swing.FontIcon;

import com.queryeer.Constants;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.output.table.ITableContextMenuActionFactory;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.dialog.ValueDialog;

/** The main panel that contains all the result set tables */
class TableOutputComponent extends JPanel implements ITableOutputComponent, SearchListener
{
    private static final String FIND = "FIND";
    static final int COLUMN_ADJUST_ROW_LIMIT = 30;
    private final List<ITableContextMenuActionFactory> contextMenuActionFactories;
    private final List<Table> tables = new ArrayList<>();
    private final TableClickedCell lastClickedCell = new TableClickedCell();
    private final TableFindDialog findDialog;

    private List<Action> contextMenuActions;

    TableOutputComponent(List<ITableContextMenuActionFactory> contextMenuActionFactories)
    {
        this.contextMenuActionFactories = requireNonNull(contextMenuActionFactories, "contextMenuActionFactories");
        setLayout(new BorderLayout());

        KeyStroke findKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
        InputMap inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(findKeyStroke, FIND);
        getActionMap().put(FIND, showFindDialogAction);

        findDialog = new TableFindDialog();
    }

    private final Action showFindDialogAction = new AbstractAction()
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            findDialog.setVisible(true);
        }
    };

    @Override
    public String getSelectedText()
    {
        Object value = lastClickedCell.value;
        return value != null ? String.valueOf(value)
                : "";
    }

    @Override
    public void searchEvent(SearchEvent e)
    {
        SearchEvent.Type type = e.getType();
        SearchContext context = e.getSearchContext();

        switch (type)
        {
            default:
            case FIND:

                Pattern pattern = null;
                if (context.isRegularExpression())
                {
                    if (context.getMatchCase())
                    {
                        pattern = Pattern.compile(context.getSearchFor());
                    }
                    else
                    {
                        pattern = Pattern.compile(context.getSearchFor(), Pattern.CASE_INSENSITIVE);
                    }
                }

                findDialog.incrementSearchState();
                int tableIndex = 0;
                for (int i = 0; i < tables.size(); i++)
                {
                    Table table = tables.get(i);
                    if (table == findDialog.currentTable)
                    {
                        tableIndex = i;
                        break;
                    }
                }

                int searchCount = 0;
                while (searchCount <= 1)
                {
                    int startRow = findDialog.currentRow;
                    int startCol = findDialog.currentCol;

                    for (int i = tableIndex; i < tables.size(); i++)
                    {
                        Table table = tables.get(i);
                        int rowCount = table.getRowCount();
                        int colCount = table.getColumnCount();

                        for (int row = startRow; row < rowCount; row++)
                        {
                            for (int col = startCol; col < colCount; col++)
                            {
                                Object value = table.getValueAt(row, col);
                                if (!match(value, context, pattern))
                                {
                                    continue;
                                }

                                // Mark for next find
                                findDialog.currentTable = table;
                                findDialog.currentRow = row;
                                findDialog.currentCol = col;

                                Rectangle bounds = new Rectangle(table.getBounds());
                                table.scrollRectToVisible(bounds);

                                table.changeSelection(row, col, false, false);
                                table.scrollRectToVisible(new Rectangle(table.getCellRect(row, col, true)));
                                return;
                            }
                        }
                    }

                    if (context.getSearchWrap())
                    {
                        searchCount++;
                        findDialog.currentTable = null;
                        findDialog.currentRow = 0;
                        findDialog.currentCol = 0;
                    }
                    else
                    {
                        break;
                    }
                }

                break;
        }
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
    public void clearState()
    {
        removeAll();
        tables.clear();
        repaint();
    }

    @Override
    public ClickedCell getLastClickedCell()
    {
        return lastClickedCell;
    }

    void showFind()
    {
        showFindDialogAction.actionPerformed(null);
    }

    private boolean match(Object val, SearchContext context, Pattern pattern)
    {
        if (val == null)
        {
            return false;
        }
        String value = String.valueOf(val);
        if (pattern != null)
        {
            return pattern.matcher(value)
                    .find();
        }
        else if (context.getMatchCase())
        {
            return value.contains(context.getSearchFor());
        }

        return StringUtils.containsIgnoreCase(value, context.getSearchFor());
    }

    private Table createTable()
    {
        if (contextMenuActions == null)
        {
            contextMenuActions = contextMenuActionFactories.stream()
                    .flatMap(f -> f.create(this)
                            .stream())
                    .sorted(Comparator.comparing(IExtensionAction::order))
                    .map(IExtensionAction::getAction)
                    .collect(toList());
        }

        Table table = new Table();
        for (Action action : contextMenuActions)
        {
            table.tablePopupMenu.add(action);
        }

        table.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                Point point = e.getPoint();
                int row = table.rowAtPoint(point);
                int col = table.columnAtPoint(point);

                lastClickedCell.value = table.getValueAt(row, col);
                lastClickedCell.header = table.getColumnName(col);

                if (e.getClickCount() == 2
                        && e.getButton() == MouseEvent.BUTTON1)
                {
                    if (row >= 0)
                    {
                        ValueDialog.showValueDialog("Value viewer - " + table.getColumnName(col), lastClickedCell.value, ValueDialog.Format.UNKOWN);
                    }
                }
                else if (e.getClickCount() == 1
                        && e.getButton() == MouseEvent.BUTTON1)
                {
                    if (col == 0)
                    {
                        table.setRowSelectionInterval(row, row);
                    }
                }
            }
        });

        return table;
    }

    /** Add new result set to this instance */
    synchronized void addResult(final Model model)
    {
        final Table resultTable = createTable();
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

    private static class TableClickedCell implements ClickedCell
    {
        private String header;
        private Object value;

        @Override
        public String getColumnHeader()
        {
            return header;
        }

        @Override
        public Object getValue()
        {
            return value;
        }
    }

    private class TableFindDialog extends FindDialog
    {
        private Table currentTable;
        private int currentCol;
        private int currentRow;
        private boolean initialSearch = true;

        TableFindDialog()
        {
            super((JFrame) SwingUtilities.getWindowAncestor(TableOutputComponent.this), TableOutputComponent.this);
            setIconImages(Constants.APPLICATION_ICONS);
            wrapCheckBox.setSelected(true);
            markAllCheckBox.setSelected(false);
            markAllCheckBox.setEnabled(false);
            wholeWordCheckBox.setEnabled(false);
            upButton.setEnabled(false);
            downButton.setEnabled(false);
        }

        void incrementSearchState()
        {
            if (initialSearch)
            {
                initialSearch = false;
                return;
            }
            currentCol++;
            if (currentCol >= currentTable.getColumnCount())
            {
                currentCol = 0;
                currentRow++;
                if (currentRow >= currentTable.getRowCount())
                {
                    currentRow = 0;

                    int index = tables.indexOf(currentTable);
                    index++;
                    currentTable = index < tables.size() ? tables.get(index)
                            : null;
                }
            }
        }

        @Override
        public void setVisible(boolean visible)
        {
            if (visible)
            {
                // Reset search state
                currentTable = null;
                currentRow = 0;
                currentCol = 0;
                initialSearch = true;
                // Start search from current selection
                for (Table table : tables)
                {
                    if (table.hasFocus())
                    {
                        currentTable = table;
                        currentRow = table.getSelectedRow();
                        currentCol = table.getSelectedColumn();
                        break;
                    }
                }
            }
            super.setVisible(visible);
        }
    }
}
