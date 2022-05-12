package com.queryeer.output.table;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.queryeer.output.table.TableOutputWriter.PairList;

/** Resulting model of a query */
class Model implements TableModel
{
    private static final String NO_COLUMN_NAME = "(No column name)";
    private final List<PairList> rows = new ArrayList<>(50);
    private String[] columns = EMPTY_STRING_ARRAY;
    private boolean complete;
    private int lastNotifyRowIndex = 0;
    private EventListenerList listenerList = new EventListenerList();

    /** Add row */
    void addRow(PairList row)
    {
        if (complete)
        {
            throw new IllegalArgumentException("This result model is completed");
        }

        rows.add(row);
        if (rows.size() >= TableOutputComponent.COLUMN_ADJUST_ROW_LIMIT)
        {
            notifyChanges();
        }
    }

    /** Called when result is completed. */
    void done()
    {
        complete = true;
        notifyChanges();
    }

    /** Set columns */
    void setColumns(String[] columns)
    {
        this.columns = requireNonNull(columns);
        SwingUtilities.invokeLater(() -> fireTableChanged(new TableModelEvent(this, TableModelEvent.HEADER_ROW)));
    }

    /** Move values, inserting nulls at 'atIndex' 'length' times */
    void moveValues(int atIndex, int length)
    {
        List<Pair<String, Object>> padding = Collections.nCopies(length, Pair.of(null, null));
        for (int i = 0; i < getRowCount(); i++)
        {
            rows.get(i)
                    .addAll(atIndex, padding);
        }
    }

    String[] getColumns()
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
        return columns.length;
    }

    @Override
    public String getColumnName(int column)
    {
        String col = columns[column];
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

        PairList row = rows.get(rowIndex);

        if (columnIndex >= row.size())
        {
            return null;
        }

        return row.get(columnIndex)
                .getValue();
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
    public void setValueAt(Object aValue, int rowIndex, int columnIndex)
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

    /**
     * Get cell label for provided object. Produces a minimal json for array and map objects
     */
    static String getLabel(Object value, int size)
    {
        StringWriter sw = new StringWriter(size);
        try (JsonGenerator generator = Utils.WRITER.getFactory()
                .createGenerator(sw))
        {
            if (value instanceof List)
            {
                generator.writeStartArray();
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;

                for (Object obj : list)
                {
                    generator.writeObject(obj);
                    if (sw.getBuffer()
                            .length() > size)
                    {
                        return sw.toString()
                                .substring(0, size)
                               + "...";
                    }
                }
            }
            else
            {
                generator.writeObject(value);
            }
        }
        catch (IOException e)
        {
        }

        return sw.getBuffer()
                .toString();
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
