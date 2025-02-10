package com.queryeer.output.table;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultRowSorter;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JPopupMenu.Separator;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TableModelEvent;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rtextarea.SearchContext;
import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.queryeer.Constants;
import com.queryeer.IconFactory;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.IDialogFactory;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.table.ITableContextMenuAction;
import com.queryeer.api.extensions.output.table.ITableContextMenuActionFactory;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;

/** The main panel that contains all the result set tables */
class TableOutputComponent extends JPanel implements ITableOutputComponent, SearchListener
{
    private static final String FIND = "FIND";
    static final int COLUMN_ADJUST_ROW_LIMIT = 30;
    private final IQueryFile queryFile;
    private final TableActionsConfigurable tableActionsConfigurable;
    private final List<ITableContextMenuActionFactory> contextMenuActionFactories;
    private final List<TableComponent> tables = new ArrayList<>();
    private final TableFindDialog findDialog;
    private final IOutputExtension extension;

    private List<ITableContextMenuAction> contextMenuActions;
    private Table lastClickedTable;
    private int lastClickedTableRow;
    private int lastClickedTableColumn;
    private IDialogFactory dialogFactory;

    class TableComponent extends JPanel
    {
        private final Table table;

        TableComponent(Table table, Map<String, Object> resultMetaData)
        {
            this.table = table;
            setLayout(new BorderLayout());

            // Add meta data panel on top of table if present
            if (!MapUtils.isEmpty(resultMetaData))
            {
                JPanel metaPanel = new JPanel(new GridBagLayout());
                // Restrict the height
                metaPanel.setMaximumSize(new Dimension(100, 18));

                JTextField value = new JTextField();
                value.setBackground(UIManager.getColor("TextField.background"));
                value.setEditable(false);
                value.setText(resultMetaData.entrySet()
                        .stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .collect(joining(", ")));

                GridBagConstraints gbc = new GridBagConstraints();
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.weightx = 1.0d;
                gbc.anchor = GridBagConstraints.WEST;
                gbc.fill = GridBagConstraints.HORIZONTAL;

                metaPanel.add(value, gbc);

                JButton showValue = new JButton("...");
                showValue.addActionListener(l -> dialogFactory.showValueDialog("Result Set Meta Data", resultMetaData, IDialogFactory.Format.JSON));

                gbc = new GridBagConstraints();
                gbc.gridx = 1;
                gbc.gridy = 0;
                gbc.anchor = GridBagConstraints.WEST;
                gbc.fill = GridBagConstraints.NONE;

                metaPanel.add(showValue, gbc);

                add(metaPanel, BorderLayout.NORTH);
            }
            add(new JScrollPane(table), BorderLayout.CENTER);
        }
    }

    TableOutputComponent(IQueryFile queryFile, IOutputExtension extension, List<ITableContextMenuActionFactory> contextMenuActionFactories, TableActionsConfigurable tableActionsConfigurable,
            IDialogFactory dialogFactory)
    {
        this.queryFile = queryFile;
        this.tableActionsConfigurable = requireNonNull(tableActionsConfigurable, "tableActionsConfigurable");
        this.extension = requireNonNull(extension, "extension");
        this.contextMenuActionFactories = requireNonNull(contextMenuActionFactories, "contextMenuActionFactories");
        this.dialogFactory = requireNonNull(dialogFactory, "dialogFactory");
        setLayout(new BorderLayout());

        UIManager.addPropertyChangeListener(uiManagerChangeListener);

        KeyStroke findKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit()
                .getMenuShortcutKeyMaskEx());
        InputMap inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(findKeyStroke, FIND);
        getActionMap().put(FIND, showFindDialogAction);

        findDialog = new TableFindDialog()
        {
            @Override
            public void setVisible(boolean b)
            {
                Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                        .getActiveWindow();

                if (b)
                {
                    setLocationRelativeTo(activeWindow);
                }
                super.setVisible(b);
            }
        };
    }

    private final PropertyChangeListener uiManagerChangeListener = new PropertyChangeListener()
    {
        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            if ("lookAndFeel".equals(evt.getPropertyName()))
            {
                for (TableComponent tc : tables)
                {
                    tc.table.adaptToLookAndFeel();
                }
            }
        }
    };

    private final Action showFindDialogAction = new AbstractAction()
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            findDialog.setVisible(true);
        }
    };

    @Override
    public IOutputExtension getExtension()
    {
        return extension;
    }

    @Override
    public String getSelectedText()
    {
        Object value = null;
        if (lastClickedTable != null)
        {
            value = lastClickedTable.getValueAt(lastClickedTableRow, lastClickedTableColumn);
        }

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
                    TableComponent table = tables.get(i);
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
                        TableComponent tc = tables.get(i);
                        int rowCount = tc.table.getRowCount();
                        int colCount = tc.table.getColumnCount();

                        for (int row = startRow; row < rowCount; row++)
                        {
                            for (int col = startCol; col < colCount; col++)
                            {
                                // Don't search in row number
                                if (col == 0)
                                {
                                    continue;
                                }

                                Object value = tc.table.getValueAt(row, col);
                                if (!match(value, context, pattern))
                                {
                                    continue;
                                }

                                // Mark for next find
                                findDialog.currentTable = tc;
                                findDialog.currentRow = row;
                                findDialog.currentCol = col;

                                Rectangle bounds = new Rectangle(tc.table.getBounds());
                                tc.table.scrollRectToVisible(bounds);

                                tc.table.changeSelection(row, col, false, false);
                                tc.table.scrollRectToVisible(new Rectangle(tc.table.getCellRect(row, col, true)));
                                return;
                            }
                            startCol = 0;
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

                JOptionPane.showMessageDialog(this, "No more hits", "Search", JOptionPane.INFORMATION_MESSAGE);
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
        return IconFactory.of(FontAwesome.TABLE);
    }

    @Override
    public Component getComponent()
    {
        return this;
    }

    @Override
    public void clearState()
    {
        // Clear reference to any table
        findDialog.currentTable = null;

        // Remove listeners
        for (TableComponent tc : tables)
        {
            tc.table.setTableHeader(null);
            Model model = (Model) tc.table.getModel();
            model.removeTableModelListener(tc.table);
        }

        tables.clear();
        removeAll();
        repaint();
    }

    @Override
    public SelectedRow getSelectedRow()
    {
        return new TableSelectedRow(lastClickedTable, lastClickedTableRow, lastClickedTableColumn);
    }

    @Override
    public void selectRow(int tableIndex, int row)
    {
        Table table = tables.get(tableIndex).table;

        Rectangle bounds = new Rectangle(table.getBounds());
        table.scrollRectToVisible(bounds);

        table.changeSelection(row, 0, false, false);
        table.scrollRectToVisible(new Rectangle(table.getCellRect(row, 0, true)));
    }

    @Override
    public IQueryFile getQueryFile()
    {
        return queryFile;
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

    private ITableContextMenuAction getAction(Object value)
    {
        int actionsSize = contextMenuActions.size();
        for (int i = 0; i < actionsSize; i++)
        {
            ITableContextMenuAction action = contextMenuActions.get(i);
            if (action.supportsLinks()
                    && action.showLink(value))
            {
                return action;
            }
        }
        return null;
    }

    private Table createTable()
    {
        if (contextMenuActions == null)
        {
            contextMenuActions = contextMenuActionFactories.stream()
                    .flatMap(f -> f.create(this)
                            .stream())
                    .sorted(Comparator.comparing(IExtensionAction::order))
                    .collect(toList());
        }

        Table table = new Table(contextMenuActions);
        // Remove default Enter move to next cell/row, we view the cell value instead
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");

        table.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyTyped(KeyEvent e)
            {
                if (e.getKeyChar() == KeyEvent.VK_ENTER)
                {
                    int row = table.getSelectedRow();
                    int col = table.getSelectedColumn();

                    if (row >= 0
                            && col >= 0)
                    {

                        Object value = table.getValueAt(row, col);
                        String header = table.getColumnName(col);
                        ITableContextMenuAction action = getAction(value);
                        if (action != null)
                        {
                            // We need to set the context row/column for external actions to have correct values returned
                            lastClickedTable = table;
                            lastClickedTableRow = row;
                            lastClickedTableColumn = col;
                            action.getAction()
                                    .actionPerformed(new ActionEvent(table, -1, ""));
                        }
                        else
                        {
                            dialogFactory.showValueDialog("Value viewer - " + header, value, IDialogFactory.Format.UNKOWN);
                        }
                    }
                }
            }
        });

        table.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                Point point = e.getPoint();
                int row = table.rowAtPoint(point);
                int col = table.columnAtPoint(point);

                lastClickedTable = table;
                lastClickedTableRow = row;
                lastClickedTableColumn = col;

                Object value = table.getValueAt(row, col);

                if (e.getClickCount() == 2
                        && e.getButton() == MouseEvent.BUTTON1)
                {
                    if (row >= 0)
                    {
                        ITableContextMenuAction action = getAction(value);
                        // Don't trigger link actions on double click
                        if (action == null)
                        {
                            dialogFactory.showValueDialog("Value viewer - " + table.getColumnName(col), value, IDialogFactory.Format.UNKOWN);
                        }
                    }
                }
                else if (e.getClickCount() == 1
                        && e.getButton() == MouseEvent.BUTTON1)
                {
                    if (col == 0)
                    {
                        table.setRowSelectionInterval(row, row);
                    }
                    else
                    {
                        // Single click then see if there are any link actions for current value
                        ITableContextMenuAction action = getAction(value);
                        if (action != null)
                        {
                            action.getAction()
                                    .actionPerformed(new ActionEvent(table, -1, ""));
                        }
                    }
                }
            }
        });

        // Add listener that sets the value for where the popup was triggered
        table.tablePopupMenu.addPopupMenuListener(new PopupMenuListener()
        {
            List<Component> contextPopupItems = new ArrayList<>();

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e)
            {
                Point point = (Point) table.getClientProperty(Table.POPUP_TRIGGER_LOCATION);
                if (point != null)
                {
                    int row = table.rowAtPoint(point);
                    int col = table.columnAtPoint(point);

                    lastClickedTable = table;
                    lastClickedTableRow = row;
                    lastClickedTableColumn = col;

                    if (row >= 0
                            && col >= 0)
                    {
                        table.setRowSelectionInterval(row, row);
                        table.setColumnSelectionInterval(col, col);
                    }

                    int count = table.tablePopupMenu.getComponentCount();

                    TableSelectedRow selectedRow = new TableSelectedRow(table, row, col);
                    for (ITableContextMenuAction action : contextMenuActions)
                    {
                        if (action.showContextMenu(selectedRow))
                        {
                            JMenuItem item = new JMenuItem(action.getAction());
                            contextPopupItems.add(item);
                            table.tablePopupMenu.insert(item, action.order());
                        }
                    }

                    // TODO: if action count exceeds a limit, wrap in a sub menu
                    List<Action> tableActions = tableActionsConfigurable.getActions(TableOutputComponent.this, selectedRow);
                    for (Action tableAction : tableActions)
                    {
                        JMenuItem item = new JMenuItem(tableAction);
                        contextPopupItems.add(item);
                        table.tablePopupMenu.add(item);
                    }

                    Object value = table.getValueAt(row, col);
                    String header = table.getColumnName(col);
                    // Only show quick filter for small values
                    if (String.valueOf(value)
                            .length() <= 100)
                    {
                        if (table.tablePopupMenu.getComponentCount() != count)
                        {
                            addSeparator(table.tablePopupMenu.getComponentCount());
                        }

                        JMenu filter = createFilterContextMenu(col, value, header);

                        contextPopupItems.add(filter);
                        table.tablePopupMenu.add(filter);
                    }

                    // Insert a separator on top if we added any items
                    if (table.tablePopupMenu.getComponentCount() != count)
                    {
                        addSeparator(count);
                    }
                }
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
            {
                if (!contextPopupItems.isEmpty())
                {
                    for (Component comp : contextPopupItems)
                    {
                        table.tablePopupMenu.remove(comp);
                    }
                    contextPopupItems.clear();
                }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e)
            {
                popupMenuWillBecomeInvisible(e);
            }

            private JMenu createFilterContextMenu(int col, Object value, String header)
            {
                JMenu filter = new JMenu("Filter");
                JMenuItem equals = new JMenuItem(new AbstractAction("Filter for " + header + " = " + value)
                {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        RowFilter<Model, Integer> rowFilter = new RowFilter<Model, Integer>()
                        {
                            @Override
                            public boolean include(Entry<? extends Model, ? extends Integer> entry)
                            {
                                if (value == null)
                                {
                                    return entry.getValue(col) == null;
                                }
                                return StringUtils.equalsIgnoreCase(entry.getStringValue(col), String.valueOf(value));
                            }
                        };
                        ((DefaultRowSorter<Model, Integer>) table.getRowSorter()).setRowFilter(rowFilter);
                    }
                });
                filter.add(equals);
                JMenuItem notEquals = new JMenuItem(new AbstractAction("Filter for " + header + " <> " + value)
                {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        RowFilter<Model, Integer> rowFilter = new RowFilter<Model, Integer>()
                        {
                            @Override
                            public boolean include(Entry<? extends Model, ? extends Integer> entry)
                            {
                                if (value == null)
                                {
                                    return entry.getValue(col) != null;
                                }
                                return !StringUtils.equalsIgnoreCase(entry.getStringValue(col), String.valueOf(value));
                            }
                        };
                        ((DefaultRowSorter<Model, Integer>) table.getRowSorter()).setRowFilter(rowFilter);
                    }
                });
                filter.add(notEquals);
                JMenuItem noFilter = new JMenuItem(new AbstractAction("No Filter for " + header)
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        ((DefaultRowSorter<?, ?>) table.getRowSorter()).setRowFilter(null);
                    }
                });
                filter.add(noFilter);
                return filter;
            }

            private void addSeparator(int position)
            {
                Separator separator = new JPopupMenu.Separator();
                contextPopupItems.add(separator);
                table.tablePopupMenu.insert(separator, position);
            }
        });

        return table;
    }

    /** Add new result set to this instance */
    synchronized void addResult(final Model model, Map<String, Object> resultMetaData)
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
        tables.add(new TableComponent(resultTable, resultMetaData));
        int size = tables.size();
        removeAll();

        // 8 rows plus header plus spacing
        int tablHeight = resultTable.getRowHeight() * 9 + 10 + (!MapUtils.isEmpty(resultMetaData) ? 18
                : 0);

        Component parent = null;

        for (int i = 0; i < size; i++)
        {
            TableComponent table = tables.get(i);
            TableComponent prevTable = i > 0 ? tables.get(i - 1)
                    : null;
            int prevTableHeight = -1;
            if (i > 0)
            {
                JScrollBar horizontalScrollBar = ((JScrollPane) prevTable.table.getParent()
                        .getParent()).getHorizontalScrollBar();

                // The least of 8 rows or actual rows in prev table
                // CSOFF
                prevTableHeight = Math.min((prevTable.table.getRowCount() + 1) * prevTable.table.getRowHeight() + 15 + prevTable.getHeight(), tablHeight);
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
                sp.setBorder(BorderFactory.createEmptyBorder());
                sp.setOrientation(JSplitPane.VERTICAL_SPLIT);
                sp.setLeftComponent(parent);
                // Adjust prev tables height
                sp.getLeftComponent()
                        .setPreferredSize(new Dimension(0, prevTableHeight));
                sp.setRightComponent(table);
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
                sp.setBorder(BorderFactory.createEmptyBorder());
                sp.setOrientation(JSplitPane.VERTICAL_SPLIT);

                // Adjust prev tables height
                if (rc instanceof TableComponent)
                {
                    sp.setLeftComponent(prevTable);
                    sp.getLeftComponent()
                            .setPreferredSize(new Dimension(0, prevTableHeight));
                }
                else if (rc instanceof JSplitPane)
                {
                    ((JSplitPane) rc).getRightComponent()
                            .setPreferredSize(new Dimension(0, prevTableHeight));
                    sp.setLeftComponent(rc);
                }
                sp.setRightComponent(table);
                sp.getRightComponent()
                        .setPreferredSize(new Dimension(0, tablHeight));

                JSplitPane topSp = new JSplitPane();
                topSp.setOrientation(JSplitPane.VERTICAL_SPLIT);

                // Replace the right component with the new split panel
                prevSp.setRightComponent(sp);
            }
        }
        add(parent instanceof JSplitPane ? new JScrollPane(parent)
                : parent, BorderLayout.CENTER);
    }

    void resizeLastTablesColumns()
    {
        // Resize last columns if not already done
        if (tables.size() > 0
                && !tables.get(tables.size() - 1).table.columnsAdjusted.get())
        {
            Table lastTable = tables.get(tables.size() - 1).table;
            lastTable.adjustColumns();
        }
    }

    private static class TableSelectedRow implements SelectedRow
    {
        private final Table table;
        private final int tableRow;
        private final int tableColumn;

        TableSelectedRow(Table table, int tableRow, int tableColumn)
        {
            this.table = table;
            this.tableRow = tableRow;
            this.tableColumn = tableColumn;
        }

        @Override
        public String getCellHeader()
        {
            if (table == null)
            {
                return null;
            }
            return table.getColumnName(tableColumn);
        }

        @Override
        public Object getCellValue()
        {
            if (table == null)
            {
                return null;
            }
            if (tableRow < 0
                    || tableRow >= table.getRowCount())
            {
                return null;
            }
            if (tableColumn < 0
                    || tableColumn >= table.getColumnCount())
            {
                return null;
            }
            return table.getValueAt(tableRow, tableColumn);
        }

        @Override
        public int getColumnCount()
        {
            if (table == null)
            {
                return 0;
            }

            return table.getColumnCount();
        }

        @Override
        public String getHeader(int columnIndex)
        {
            if (table == null)
            {
                return null;
            }

            return table.getColumnName(columnIndex);
        }

        @Override
        public Object getValue(int columnIndex)
        {
            if (table == null
                    || tableRow < 0
                    || tableRow >= table.getRowCount())
            {
                return null;
            }

            return table.getValueAt(tableRow, columnIndex);
        }
    }

    private class TableFindDialog extends FindDialog
    {
        private TableComponent currentTable;
        private int currentCol;
        private int currentRow;
        private boolean initialSearch = true;

        TableFindDialog()
        {
            super((JFrame) SwingUtilities.getWindowAncestor(TableOutputComponent.this), TableOutputComponent.this);
            setIconImages(Constants.APPLICATION_ICONS);

            context.setSearchWrap(true);
            context.setMarkAll(false);

            markAllCheckBox.setSelected(false);
            markAllCheckBox.setEnabled(false);
            wholeWordCheckBox.setEnabled(false);
            upButton.setEnabled(false);
            downButton.setEnabled(false);

            refreshUIFromContext();
            setLocationRelativeTo(getParent());
        }

        void incrementSearchState()
        {
            if (initialSearch)
            {
                initialSearch = false;
                return;
            }
            if (currentTable == null)
            {
                return;
            }
            currentCol++;
            if (currentCol >= currentTable.table.getColumnCount())
            {
                currentCol = 0;
                currentRow++;
                if (currentRow >= currentTable.table.getRowCount())
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
                for (TableComponent tc : tables)
                {
                    if (tc.table.hasFocus())
                    {
                        currentTable = tc;
                        currentRow = tc.table.getSelectedRow();
                        currentCol = tc.table.getSelectedColumn();
                        break;
                    }
                }
            }

            super.setVisible(visible);
        }
    }

    @Override
    public void dispose()
    {
        UIManager.removePropertyChangeListener(uiManagerChangeListener);
    }
}
