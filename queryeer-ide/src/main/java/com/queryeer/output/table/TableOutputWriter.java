package com.queryeer.output.table;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.api.IQueryFile;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Writer that writes object structure from a projection. */
class TableOutputWriter implements OutputWriter
{
    /** Current model */
    private Model model;
    private IQueryFile queryFile;

    TableOutputWriter(IQueryFile queryFile)
    {
        this.queryFile = Objects.requireNonNull(queryFile, "queryFile");
    }

    private final Stack<Object> parent = new Stack<>();
    private final Stack<String> currentField = new Stack<>();

    @Override
    public void initResult(String[] columns)
    {
        // Print previous model row count
        if (model != null)
        {
            // model.done();

            int rowCount = model.getRowCount();
            queryFile.getMessagesWriter()
                    .println(String.valueOf(rowCount) + " row(s) selected" + System.lineSeparator());
        }

        this.model = new Model();
        this.model.setColumns(columns);
        // Add model to output component
        getTablesOutputComponent().addResult(this.model);
    }

    @Override
    public void close()
    {
        getTablesOutputComponent().resizeLastTablesColumns();
        // model.done();
    }

    @Override
    public void endRow()
    {
        queryFile.incrementTotalRowCount();

        if (parent.isEmpty())
        {
            return;
        }

        // Adjust columns
        PairList pairList = getValue(model.getRowCount() + 1);
        // Adjust columns in model
        if (!pairList.matchesModelColumns)
        {
            int count = model.getColumnCount();
            int listCount = pairList.size();
            int newColumnsAdjust = 0;
            boolean changeModelColumns = count != listCount;

            // Don't need to check row_id (first) columns
            for (int i = 1; i < count; i++)
            {
                String modelColumn = model.getColumns()[i];
                String listColumn = (i + newColumnsAdjust) < listCount ? pairList.get(i + newColumnsAdjust)
                        .getKey()
                        : null;

                // New column that is about to be added last, mark
                // model to be changed and move on
                if (listColumn == null)
                {
                    changeModelColumns = true;
                    continue;
                }

                // Find out if we should pad previous values or new rows values
                if (!modelColumn.equalsIgnoreCase(listColumn))
                {
                    // Step forward in pairList until we find the current column
                    int c = findListColumn(pairList, modelColumn, i + newColumnsAdjust);
                    // CSOFF
                    if (c == -1)
                    // CSON
                    {
                        // Pad current row with null
                        pairList.add(i + newColumnsAdjust, Pair.of(modelColumn, null));
                    }
                    else
                    {
                        changeModelColumns = true;
                        newColumnsAdjust += c;
                        model.moveValues(i, c);
                    }
                }
            }

            if (changeModelColumns)
            {
                model.setColumns(pairList.columns.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
            }
        }

        model.addRow(pairList);
    }

    Model getModel()
    {
        return model;
    }

    /**
     * Tries to find provided column in povided list starting at start index.
     *
     * @return Returns number of steps away or -1 if not found
     */
    private int findListColumn(PairList list, String column, int startIndex)
    {
        int size = list.size();
        int steps = 0;
        for (int i = startIndex; i < size; i++)
        {
            if (column.equalsIgnoreCase(list.get(i)
                    .getKey()))
            {
                return steps;
            }
            steps++;
        }
        return -1;
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
            parent.push(new PairList(10));
            // CSON
        }
        else
        {
            parent.push(new LinkedHashMap<>());
        }
    }

    @Override
    public void endObject()
    {
        putValue(parent.pop());
    }

    @Override
    public void startArray()
    {
        parent.push(new ArrayList<>());
    }

    @Override
    public void endArray()
    {
        putValue(parent.pop());
    }

    @SuppressWarnings("unchecked")
    private void putValue(Object value)
    {
        // Top of stack put value back
        if (parent.isEmpty())
        {
            parent.push(value);
            return;
        }

        Object p = parent.peek();

        if (p instanceof PairList)
        {
            // Find out where to put this entry
            PairList list = (PairList) p;
            String column = currentField.pop();
            // Adjust for row_id column
            int columnIndex = list.size() + 1;

            // Flag that the list is not matching model columns
            // model needs to adjust later on
            if (list.matchesModelColumns
                    && (columnIndex >= model.getColumnCount()
                            || !column.equalsIgnoreCase(model.getColumns()[columnIndex])))
            {
                list.matchesModelColumns = false;
            }

            list.add(Pair.of(column, value));
        }
        else if (p instanceof Map)
        {
            ((Map<String, Object>) p).put(currentField.pop(), value);
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
    private PairList getValue(int rowNumber)
    {
        currentField.clear();
        Object v = parent.pop();
        if (!(v instanceof PairList))
        {
            throw new RuntimeException("Expected a list of string/value pairs but got " + v);
        }

        PairList result = (PairList) v;
        result.add(0, Pair.of("", rowNumber));
        return (PairList) v;
    }

    /** Pair list. */
    static class PairList extends ArrayList<Pair<String, Object>>
    {
        static final PairList EMPTY = new PairList(0);
        private final List<String> columns;
        private boolean matchesModelColumns = true;

        private PairList(int capacity)
        {
            super(capacity);
            columns = new ArrayList<>(capacity);
        }

        List<String> getColumns()
        {
            return columns;
        }

        @Override
        public void add(int index, Pair<String, Object> pair)
        {
            columns.add(index, pair.getKey());
            super.add(index, pair);
        }

        @Override
        public boolean add(Pair<String, Object> pair)
        {
            columns.add(pair.getKey());
            return super.add(pair);
        }
    }
}