package com.queryeer.output.table;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.swing.SwingUtilities;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.QueryeerOutputWriter;

import se.kuseman.payloadbuilder.api.execution.EpochDateTime;
import se.kuseman.payloadbuilder.api.execution.EpochDateTimeOffset;
import se.kuseman.payloadbuilder.api.execution.UTF8String;

/** Writer that writes object structure from a projection. */
class TableOutputWriter implements QueryeerOutputWriter
{
    private final TableOutputComponent tableOutputComponent;
    private final IQueryFile queryFile;
    /** Current model */
    private Model model;

    TableOutputWriter(IQueryFile queryFile)
    {
        this.queryFile = Objects.requireNonNull(queryFile, "queryFile");
        this.tableOutputComponent = queryFile.getOutputComponent(TableOutputComponent.class);
    }

    private final Deque<Object> parent = new ArrayDeque<>();
    private final Deque<String> currentField = new ArrayDeque<>();
    private final Row row = new Row();
    private int nestLevel = 0;

    @Override
    public void initResult(String[] columns, Map<String, Object> resultMetaData)
    {
        this.model = new Model();

        List<String> allColumns = new ArrayList<>(asList(columns));
        // Insert the row id column first
        allColumns.add(0, "");

        this.model.setColumns(allColumns);

        // Need a sync call here else we will have races on fast queries where we append wrong models
        try
        {
            SwingUtilities.invokeAndWait(() -> tableOutputComponent.addResult(model, resultMetaData));
        }
        catch (InvocationTargetException | InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void close()
    {
        tableOutputComponent.internCache.clear();
        row.clear();
        SwingUtilities.invokeLater(() -> tableOutputComponent.resizeLastTablesColumns());
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
    public void startRow()
    {
        nestLevel = -1;
        row.clear();
        // Add the row number entry
        // NOTE! We set a constant here, the actual row number value is
        // set in Model later on
        row.add("", 0);
    }

    @Override
    public void endRow()
    {
        queryFile.incrementTotalRowCount();
        model.addRow(row);
    }

    @Override
    public void writeFieldName(String name)
    {
        currentField.push(name);
    }

    @Override
    public void writeDateTime(EpochDateTime datetime)
    {
        writeValue(datetime.getLocalDateTime());
    }

    @Override
    public void writeDateTimeOffset(EpochDateTimeOffset datetimeOffset)
    {
        writeValue(datetimeOffset.getZonedDateTime());
    }

    @Override
    public void writeValue(Object input)
    {
        Object value = input;
        if (value instanceof Iterator it)
        {
            startArray();
            while (it.hasNext())
            {
                writeValue(it.next());
            }
            endArray();
            return;
        }
        else if (value instanceof Reader r)
        {
            try (Reader reader = r)
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
        nestLevel++;
        // Root level, don't do anything since we add stuff to Row instance
        if (nestLevel == 0)
        {
            return;
        }
        parent.addFirst(new LinkedHashMap<>());
    }

    @Override
    public void endObject()
    {
        boolean nested = nestLevel > 0;
        nestLevel--;
        // Only put non root values
        if (nested)
        {
            putValue(parent.removeFirst());
        }
    }

    @Override
    public void startArray()
    {
        nestLevel++;
        parent.addFirst(new ArrayList<>());
    }

    @Override
    public void endArray()
    {
        nestLevel--;
        putValue(parent.removeFirst());
    }

    @SuppressWarnings("unchecked")
    private void putValue(Object v)
    {
        // Intern the value
        Object value = internValue(v);
        Object p = nestLevel == 0 ? row
                : parent.peekFirst();

        if (p instanceof Row row)
        {
            row.add(currentField.removeFirst(), value);
        }
        else if (p instanceof Map map)
        {
            map.put(currentField.removeFirst(), value);
        }
        else if (p instanceof List list)
        {
            list.add(value);
        }
    }

    private Object internValue(Object value)
    {
        if (value == null)
        {
            return value;
        }
        if (!(value instanceof Map
                || value instanceof Collection
                || value.getClass()
                        .isArray()))
        {
            // MutableObjectVector turns all String's into UTF8String so we miss the interning of these
            // so instead do this transformation here.
            if (value instanceof String str)
            {
                value = UTF8String.from(str);
            }
            return tableOutputComponent.internCache.computeIfAbsent(value, Function.identity());
        }
        return value;
    }

    /** Root structure for rows */
    static class Row extends ArrayList<Pair<String, Object>>
    {
        void add(String column, Object value)
        {
            super.add(Pair.of(column, value));
        }
    }
}
