package com.queryeer.output.table;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import com.queryeer.output.table.TableOutputWriter.RowList;

/** Resulting model of a query */
class Model implements TableModel
{
    private static final String NO_COLUMN_NAME = "(No column name)";
    private static final String IMAGE_PREFIX = "__queryeerimage__";
    private final List<RowList> rows = new ArrayList<>(50);
    private List<String> columns = emptyList();
    private Set<Integer> imageColumnIndices = new HashSet<>();
    private int lastNotifyRowIndex = 0;
    private EventListenerList listenerList = new EventListenerList();

    private List<Class<?>> columnTypes = new ArrayList<>();

    Map<Object, Object> internCache = new HashMap<>();

    /** Add row */
    void addRow(RowList row)
    {
        int size = row.size();
        int diff = Math.max(size, columnTypes.size()) - columnTypes.size();
        if (diff > 0)
        {
            columnTypes.addAll(Collections.nCopies(diff, null));
        }

        for (int i = 0; i < size; i++)
        {
            Object value = row.get(i);
            // Intern values to minimize heap allocations
            row.set(i, internCache.computeIfAbsent(value, k -> value));

            // Try to determine types of columns
            Class<?> clazz = value == null ? null
                    : value.getClass();
            if (clazz != null
                    && clazz != Boolean.class)
            {
                Class<?> columnType = columnTypes.get(i);

                // No class set, set the values class
                if (columnType == null)
                {
                    columnTypes.set(i, clazz);
                }
                // .. the class differs from previous values => set to Object.class which is the default in a swing table
                else if (columnType != Object.class
                        && columnType != clazz)
                {
                    columnTypes.set(i, Object.class);
                }
            }
        }

        rows.add(row);
        if (rows.size() >= TableOutputComponent.COLUMN_ADJUST_ROW_LIMIT)
        {
            notifyChanges(false);
        }
    }

    /** Set columns */
    void setColumns(List<String> columns)
    {
        if (requireNonNull(columns).size() > 0)
        {
            this.columns = columns;
            int size = columns.size();
            for (int i = 0; i < size; i++)
            {
                if (Strings.CI.startsWith(columns.get(i), IMAGE_PREFIX))
                {
                    imageColumnIndices.add(i);
                }
            }
            SwingUtilities.invokeLater(() -> fireTableChanged(new TableModelEvent(this, TableModelEvent.HEADER_ROW)));
        }
    }

    List<String> getColumns()
    {
        return columns;
    }

    @Override
    public int getRowCount()
    {
        return rows.size();
    }

    @Override
    public int getColumnCount()
    {
        return columns.size();
    }

    @Override
    public String getColumnName(int column)
    {
        String col = columns.get(column);
        if (column > 0
                && isBlank(col))
        {
            return NO_COLUMN_NAME;
        }
        // strip the prefix from the special image column
        else if (imageColumnIndices.contains(column))
        {
            return StringUtils.substring(col, IMAGE_PREFIX.length());
        }
        return col;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        if (rowIndex >= rows.size())
        {
            return null;
        }

        RowList row = rows.get(rowIndex);
        if (columnIndex >= row.size())
        {
            return null;
        }

        Object value = row.get(columnIndex);

        if (value != null
                && imageColumnIndices.contains(columnIndex)
                && !(value instanceof QueryeerImage))
        {
            value = new QueryeerImage(value);
            row.set(columnIndex, value);
        }

        return value;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex)
    {
        return false;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        Class<?> clazz = columnIndex < columnTypes.size() ? columnTypes.get(columnIndex)
                : null;
        return clazz == null ? Object.class
                : clazz;
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex)
    {
    }

    @Override
    public void addTableModelListener(TableModelListener l)
    {
        listenerList.add(TableModelListener.class, l);
    }

    @Override
    public void removeTableModelListener(TableModelListener l)
    {
        listenerList.remove(TableModelListener.class, l);
    }

    /** Notifies changes since last notify */
    void notifyChanges(boolean force)
    {
        int size = rows.size() - 1;
        if (size >= lastNotifyRowIndex
                || force)
        {
            TableModelEvent event = new TableModelEvent(this, lastNotifyRowIndex, size, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT);
            if (SwingUtilities.isEventDispatchThread())
            {
                fireTableChanged(event);
            }
            else
            {
                SwingUtilities.invokeLater(() -> fireTableChanged(event));
            }
            lastNotifyRowIndex = size;
        }
    }

    void notifyCellChange(int row, int column)
    {
        TableModelEvent event = new TableModelEvent(this, row, row, column, TableModelEvent.UPDATE);
        if (SwingUtilities.isEventDispatchThread())
        {
            fireTableChanged(event);
        }
        else
        {
            SwingUtilities.invokeLater(() -> fireTableChanged(event));
        }
    }

    private void fireTableChanged(TableModelEvent e)
    {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2)
        {
            if (listeners[i] == TableModelListener.class)
            {
                ((TableModelListener) listeners[i + 1]).tableChanged(e);
            }
        }
    }

    /** Image holder for special columns with images. */
    static class QueryeerImage
    {
        private final Object rawValue;
        private volatile Icon icon;
        private volatile boolean loading;

        QueryeerImage(Object rawValue)
        {
            this.rawValue = requireNonNull(rawValue);
        }

        public Object getRawValue()
        {
            return rawValue;
        }

        Icon getIcon()
        {
            return icon;
        }

        void setIcon(Icon icon)
        {
            this.icon = icon;
        }

        boolean isLoading()
        {
            return loading;
        }

        void setLoading(boolean loading)
        {
            this.loading = loading;
        }

        @Override
        public String toString()
        {
            return String.valueOf(rawValue);
        }
    }
}
