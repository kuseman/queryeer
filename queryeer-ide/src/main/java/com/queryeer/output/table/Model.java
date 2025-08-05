package com.queryeer.output.table;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.output.table.TableOutputWriter.Row;

import se.kuseman.payloadbuilder.api.catalog.Column.Type;
import se.kuseman.payloadbuilder.api.catalog.ResolvedType;
import se.kuseman.payloadbuilder.api.execution.Decimal;
import se.kuseman.payloadbuilder.api.execution.EpochDateTime;
import se.kuseman.payloadbuilder.api.execution.EpochDateTimeOffset;
import se.kuseman.payloadbuilder.api.execution.UTF8String;
import se.kuseman.payloadbuilder.api.execution.vector.MutableValueVector;
import se.kuseman.payloadbuilder.core.execution.vector.BufferAllocator;
import se.kuseman.payloadbuilder.core.execution.vector.VectorFactory;

/** Resulting model of a query */
class Model implements TableModel
{
    private static final String NO_COLUMN_NAME = "(No column name)";
    private static final String IMAGE_PREFIX = "__queryeerimage__";
    private static final int INIT_ROW_SIZE = 50;
    private final VectorFactory vectorFactory;
    private List<String> columns = emptyList();
    private Set<Integer> imageColumnIndices = new HashSet<>();
    private int lastNotifyRowIndex = 0;
    private EventListenerList listenerList = new EventListenerList();

    private int rowCount = 0;
    private List<Class<?>> columnTypes = new ArrayList<>();
    private List<MutableValueVector> columnVectors = new ArrayList<>();

    Model()
    {
        vectorFactory = new VectorFactory(new BufferAllocator());
    }

    /** Add row */
    void addRow(Row row)
    {
        int size = row.size();
        int diff = Math.max(size, columnTypes.size()) - columnTypes.size();
        // Make sure the lists are in par size wise with the appending row
        if (diff > 0)
        {
            columnTypes.addAll(Collections.nCopies(diff, null));
            columnVectors.addAll(Collections.nCopies(diff, null));
        }
        diff = Math.max(size, columns.size()) - columns.size();
        if (diff > 0)
        {
            columns.addAll(Collections.nCopies(diff, null));
        }
        boolean columnsChanged = false;
        for (int i = 0; i < size; i++)
        {
            Pair<String, Object> pair = row.get(i);
            String column = pair.getKey();
            Object value = pair.getValue();

            // First row is integer row number
            if (i == 0)
            {
                if (columnTypes.get(i) == null)
                {
                    columnTypes.set(0, Integer.class);
                }

                MutableValueVector vector = columnVectors.get(0);
                if (vector == null)
                {
                    vector = vectorFactory.getMutableVector(ResolvedType.of(Type.Int), INIT_ROW_SIZE);
                    columnVectors.set(0, vector);
                }
                vector.setInt(rowCount, rowCount + 1);
            }
            // All other columns are objects
            else
            {
                int vectorOrdinal = -1;
                MutableValueVector vector = null;
                // Find the column index for the current row column
                // Start to search from current rows column index, this to adapt to multiple
                // columns with the same name on the same row
                int columnSize = columns.size();
                for (int j = i; j < columnSize; j++)
                {
                    // Find the first matching column and put the value there
                    if (column.equalsIgnoreCase(columns.get(j)))
                    {
                        vector = columnVectors.get(j);
                        if (vector == null)
                        {
                            vector = vectorFactory.getMutableVector(ResolvedType.ANY, INIT_ROW_SIZE);
                            columnVectors.set(j, vector);
                        }
                        vectorOrdinal = j;
                        break;
                    }
                }
                // No column found, append the column last (at current index)
                if (vector == null)
                {
                    vector = vectorFactory.getMutableVector(ResolvedType.ANY, INIT_ROW_SIZE);
                    columnVectors.set(i, vector);
                    columns.set(i, column);
                    vectorOrdinal = i;
                    columnsChanged = true;

                    if (Strings.CI.startsWith(column, IMAGE_PREFIX))
                    {
                        imageColumnIndices.add(i);
                    }
                }

                vector.setAny(rowCount, value);

                // Try to determine types of columns
                Class<?> clazz = value == null ? null
                        : value.getClass();
                if (clazz != null
                        && clazz != Boolean.class)
                {
                    Class<?> columnType = columnTypes.get(vectorOrdinal);

                    // No class set, set the values class
                    if (columnType == null)
                    {
                        columnTypes.set(vectorOrdinal, clazz);
                    }
                    // .. the class differs from previous values => set to Object.class which is the default in a swing table
                    else if (columnType != Object.class
                            && columnType != clazz)
                    {
                        columnTypes.set(vectorOrdinal, Object.class);
                    }
                }
            }
        }

        // Normalize all vectors and append null to the ones that didn't get a value
        size = columnVectors.size();
        for (int i = 0; i < size; i++)
        {
            MutableValueVector vector = columnVectors.get(i);
            if (vector != null
                    && vector.size() != rowCount + 1)
            {
                vector.setNull(rowCount);
            }
        }

        if (columnsChanged)
        {
            SwingUtilities.invokeLater(() -> fireTableChanged(new TableModelEvent(this, TableModelEvent.HEADER_ROW)));
        }

        rowCount++;
        if (rowCount >= TableOutputComponent.COLUMN_ADJUST_ROW_LIMIT)
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
        return rowCount;
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
        if (rowIndex >= rowCount)
        {
            return null;
        }

        MutableValueVector vector = columnVectors.get(columnIndex);

        if (vector == null)
        {
            return null;
        }

        Object value = vector.valueAsObject(rowIndex);

        if (value != null
                && imageColumnIndices.contains(columnIndex)
                && !(value instanceof QueryeerImage))
        {
            value = new QueryeerImage(value);
            vector.setAny(rowIndex, value);
        }

        // Unwrap PLB objects here to avoid problems in actions etc. that checks for string values etc.
        if (value instanceof UTF8String str)
        {
            value = str.toString();
        }
        else if (value instanceof Decimal d)
        {
            value = d.asBigDecimal();
        }
        else if (value instanceof EpochDateTime d)
        {
            value = d.getLocalDateTime();
        }
        else if (value instanceof EpochDateTimeOffset d)
        {
            value = d.getZonedDateTime();
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
        int size = rowCount - 1;
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
