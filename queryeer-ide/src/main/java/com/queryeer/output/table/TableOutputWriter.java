package com.queryeer.output.table;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.SwingUtilities;

import org.apache.commons.io.IOUtils;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.QueryeerOutputWriter;

/** Writer that writes object structure from a projection. */
class TableOutputWriter implements QueryeerOutputWriter
{
    /** Current model */
    private Model model;
    private IQueryFile queryFile;

    TableOutputWriter(IQueryFile queryFile)
    {
        this.queryFile = Objects.requireNonNull(queryFile, "queryFile");
    }

    private final Deque<Object> parent = new ArrayDeque<>();
    private final Deque<String> currentField = new ArrayDeque<>();

    @Override
    public void initResult(String[] columns, Map<String, Object> resultMetaData)
    {
        // Print previous model row count
        if (model != null)
        {
            model.internCache.clear();
            model.internCache = null;
        }

        this.model = new Model();

        List<String> allColumns = new ArrayList<>(asList(columns));
        // Insert the row id column first
        allColumns.add(0, "");

        this.model.setColumns(allColumns);

        // Need a sync call here else we will have races on fast queries where we append wrong models
        try
        {
            SwingUtilities.invokeAndWait(() -> getTablesOutputComponent().addResult(model, resultMetaData));
        }
        catch (InvocationTargetException | InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void close()
    {
        if (model != null)
        {
            model.internCache.clear();
            model.internCache = null;
        }
        SwingUtilities.invokeLater(() -> getTablesOutputComponent().resizeLastTablesColumns());
    }

    @Override
    public void flush()
    {
        if (model != null)
        {
            model.notifyChanges(true);
        }
    }

    @Override
    public void endRow()
    {
        queryFile.incrementTotalRowCount();

        if (parent.isEmpty())
        {
            return;
        }

        RowList rowList = getValue(model.getRowCount() + 1);
        ColumnsMerger.merge(model, rowList);
    }

    @Override
    public void writeFieldName(String name)
    {
        currentField.push(name);
    }

    @Override
    public void writeValue(Object input)
    {
        Object value = input;
        if (value instanceof Iterator)
        {
            @SuppressWarnings("unchecked")
            Iterator<Object> it = (Iterator<Object>) value;
            startArray();
            while (it.hasNext())
            {
                writeValue(it.next());
            }
            endArray();
            return;
        }
        else if (value instanceof Reader)
        {
            try (Reader reader = (Reader) value)
            {
                value = IOUtils.toString(reader);
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error reading reader to string", e);
            }
        }

        putValue(value);
    }

    @Override
    public void startObject()
    {
        // Root object should not be a map
        // since we might have duplicate column names
        if (parent.size() == 0)
        {
            // CSOFF
            parent.addFirst(new RowList(model.getRowCount() + 1, 10));
            // CSON
        }
        else
        {
            parent.addFirst(new LinkedHashMap<>());
        }
    }

    @Override
    public void endObject()
    {
        putValue(parent.removeFirst());
    }

    @Override
    public void startArray()
    {
        parent.addFirst(new ArrayList<>());
    }

    @Override
    public void endArray()
    {
        putValue(parent.removeFirst());
    }

    @SuppressWarnings("unchecked")
    private void putValue(Object value)
    {
        // Top of stack put value back
        if (parent.isEmpty())
        {
            parent.addFirst(value);
            return;
        }

        Object p = parent.peekFirst();

        if (p instanceof RowList)
        {
            // Find out where to put this entry
            RowList list = (RowList) p;
            String column = currentField.removeFirst();
            int columnIndex = list.size();

            // Flag that the list is not matching model columns
            // model needs to adjust later on
            if (list.matchesModelColumns
                    && (columnIndex >= model.getColumnCount()
                            || !column.equalsIgnoreCase(model.getColumns()
                                    .get(columnIndex))))
            {
                list.matchesModelColumns = false;
            }

            list.add(column, value);
        }
        else if (p instanceof Map)
        {
            ((Map<String, Object>) p).put(currentField.removeFirst(), value);
        }
        else if (p instanceof List)
        {
            ((List<Object>) p).add(value);
        }
    }

    private TableOutputComponent getTablesOutputComponent()
    {
        return queryFile.getOutputComponent(TableOutputComponent.class);
    }

    /** Returns written value and clears state. */
    private RowList getValue(int rowNumber)
    {
        currentField.clear();
        Object v = parent.removeFirst();
        if (!(v instanceof RowList))
        {
            throw new RuntimeException("Expected a RowList but got " + v);
        }

        RowList result = (RowList) v;
        return result;
    }

    /** Pair list. */
    static class RowList extends ArrayList<Object>
    {
        List<String> columns;
        boolean matchesModelColumns = true;

        RowList(int rowId, int capacity)
        {
            super(capacity);
            columns = new ArrayList<>(capacity);
            // Add first row id column
            add("", rowId);
        }

        void add(String column, Object value)
        {
            columns.add(column);
            add(value);
        }
    }
}