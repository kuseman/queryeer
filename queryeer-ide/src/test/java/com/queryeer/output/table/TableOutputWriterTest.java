package com.queryeer.output.table;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.queryeer.api.IQueryFile;

/** Test {@link TableOutputWriter} */
public class TableOutputWriterTest extends Assert
{
    @Test
    public void test_column_expanding_with_no_name_columns()
    {
        IQueryFile queryFile = Mockito.mock(IQueryFile.class);
        Mockito.when(queryFile.getOutputComponent(TableOutputComponent.class))
                .thenReturn(Mockito.mock(TableOutputComponent.class));
        TableOutputWriter ow = new TableOutputWriter(queryFile);

        ow.initResult(new String[0]);

        ow.startRow();
        ow.endRow();

        // First row
        ow.startRow();
        ow.startObject();
        ow.writeFieldName("");
        ow.writeValue(123);
        ow.endObject();
        ow.endRow();

        Model model = ow.getModel();

        assertArrayEquals(new String[] { "", "" }, model.getColumns());
        assertArrayEquals(new Object[] { 1, 123 }, row(model, 0));

        // Second row
        ow.startRow();
        ow.startObject();
        ow.writeFieldName("");
        ow.writeValue(456);
        ow.writeFieldName("");
        ow.writeValue(789);
        ow.endObject();
        ow.endRow();

        assertArrayEquals(new String[] { "", "", "" }, model.getColumns());
        assertArrayEquals(new Object[] { 1, 123, null }, row(model, 0));
        assertArrayEquals(new Object[] { 2, 456, 789 }, row(model, 1));
    }

    @Test
    public void test_column_expanding()
    {
        IQueryFile queryFile = Mockito.mock(IQueryFile.class);
        Mockito.when(queryFile.getOutputComponent(TableOutputComponent.class))
                .thenReturn(Mockito.mock(TableOutputComponent.class));
        TableOutputWriter ow = new TableOutputWriter(queryFile);
        ow.initResult(new String[0]);

        ow.startRow();
        ow.endRow();

        // First row
        ow.startRow();
        ow.startObject();
        ow.writeFieldName("col1");
        ow.writeValue(123);
        ow.endObject();
        ow.endRow();

        Model model = ow.getModel();

        String[] actual = model.getColumns();
        assertArrayEquals(new String[] { "", "col1" }, actual);
        assertArrayEquals(new Object[] { 1, 123 }, row(model, 0));

        // Second row
        ow.startRow();
        ow.startObject();
        ow.writeFieldName("col1");
        ow.writeValue(456);
        ow.endObject();
        ow.endRow();

        // Assert that the columns hasn't be changed
        assertSame(model.getColumns(), actual);
        assertArrayEquals(new Object[] { 1, 123 }, row(model, 0));
        assertArrayEquals(new Object[] { 2, 456 }, row(model, 1));

        // Third row, new column last
        ow.startRow();
        ow.startObject();
        ow.writeFieldName("col1");
        ow.writeValue(789);
        ow.writeFieldName("col2");
        ow.writeValue("hello");
        ow.endObject();
        ow.endRow();

        assertArrayEquals(new String[] { "", "col1", "col2" }, model.getColumns());
        assertArrayEquals(new Object[] { 1, 123, null }, row(model, 0));
        assertArrayEquals(new Object[] { 2, 456, null }, row(model, 1));
        assertArrayEquals(new Object[] { 3, 789, "hello" }, row(model, 2));

        // Fourth row, new column in the middle
        ow.startRow();
        ow.startObject();
        ow.writeFieldName("col1");
        ow.writeValue(1337);
        ow.writeFieldName("newOne");
        ow.writeValue(true);
        ow.writeFieldName("col2");
        ow.writeValue("world");
        ow.endObject();
        ow.endRow();

        assertArrayEquals(new String[] { "", "col1", "newOne", "col2" }, model.getColumns());
        assertArrayEquals(new Object[] { 1, 123, null, null }, row(model, 0));
        assertArrayEquals(new Object[] { 2, 456, null, null }, row(model, 1));
        assertArrayEquals(new Object[] { 3, 789, null, "hello" }, row(model, 2));
        assertArrayEquals(new Object[] { 4, 1337, true, "world" }, row(model, 3));

        // Fifth row, less columns than previous rows
        ow.startRow();
        ow.startObject();
        ow.writeFieldName("col1");
        ow.writeValue(99999);
        ow.writeFieldName("col2");
        ow.writeValue("666");
        ow.endObject();
        ow.endRow();

        assertArrayEquals(new String[] { "", "col1", "newOne", "col2" }, model.getColumns());
        assertArrayEquals(new Object[] { 1, 123, null, null }, row(model, 0));
        assertArrayEquals(new Object[] { 2, 456, null, null }, row(model, 1));
        assertArrayEquals(new Object[] { 3, 789, null, "hello" }, row(model, 2));
        assertArrayEquals(new Object[] { 4, 1337, true, "world" }, row(model, 3));
        assertArrayEquals(new Object[] { 5, 99999, null, "666" }, row(model, 4));
    }

    private Object[] row(Model model, int row)
    {
        Object[] result = new Object[model.getColumnCount()];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = model.getValueAt(row, i);
        }
        return result;
    }
}
