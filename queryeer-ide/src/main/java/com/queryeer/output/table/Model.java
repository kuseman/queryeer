package com.queryeer.output.table;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.queryeer.output.table.TableOutputWriter.RowList;

/** Resulting model of a query */
class Model implements TableModel
{
    private static final String NO_COLUMN_NAME = "(No column name)";
    private final List<RowList> rows = new ArrayList<>(50);
    private List<String> columns = emptyList();
    private int lastNotifyRowIndex = 0;
    private EventListenerList listenerList = new EventListenerList();

    Map<Object, Object> internCache = new HashMap<>();

    /** Add row */
    void addRow(RowList row)
    {
        // Intern values to minimize heap allocations
        int size = row.size();
        for (int i = 0; i < size; i++)
        {
            Object value = row.get(i);
            row.set(i, internCache.computeIfAbsent(value, k -> value));
        }

        rows.add(row);
        if (rows.size() >= TableOutputComponent.COLUMN_ADJUST_ROW_LIMIT)
        {
            notifyChanges();
        }
    }

    /** Set columns */
    void setColumns(List<String> columns, boolean notify)
    {
        if (requireNonNull(columns).size() > 0)
        {
            this.columns = columns;
            if (notify)
            {
                Runnable r = () -> fireTableChanged(new TableModelEvent(this, TableModelEvent.HEADER_ROW));
                if (SwingUtilities.isEventDispatchThread())
                {
                    r.run();
                }
                else
                {
                    SwingUtilities.invokeLater(r);
                }
            }
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

        return row.get(columnIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex)
    {
        return false;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        return Object.class;
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
    void notifyChanges()
    {
        int size = rows.size() - 1;
        if (size >= lastNotifyRowIndex)
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

    /** Return pretty json for provided value */
    static String getPrettyJson(Object value)
    {
        try
        {
            return Utils.WRITER.writeValueAsString(value);
        }
        catch (JsonProcessingException e)
        {
            return StringUtils.EMPTY;
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
}
