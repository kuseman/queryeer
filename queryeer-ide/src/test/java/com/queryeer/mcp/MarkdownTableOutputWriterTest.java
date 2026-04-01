package com.queryeer.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import se.kuseman.payloadbuilder.api.execution.UTF8String;

/** Tests for {@link MarkdownTableOutputWriter} */
class MarkdownTableOutputWriterTest
{
    private MarkdownTableOutputWriter writer;

    @BeforeEach
    void setup()
    {
        writer = new MarkdownTableOutputWriter();
    }

    @Test
    void single_column_single_row()
    {
        writer.initResult(new String[] { "name" });
        writer.startRow();
        writer.writeValue("Alice");
        writer.endRow();

        assertEquals("| name |\n| --- |\n| Alice |\n", writer.getResult());
    }

    @Test
    void multi_column_multi_row()
    {
        writer.initResult(new String[] { "id", "name", "active" });
        writer.startRow();
        writer.writeInt(1);
        writer.writeString(UTF8String.from("Alice"));
        writer.writeBool(true);
        writer.endRow();
        writer.startRow();
        writer.writeInt(2);
        writer.writeString(UTF8String.from("Bob"));
        writer.writeBool(false);
        writer.endRow();

        String expected = "| id | name | active |\n" + "| --- | --- | --- |\n" + "| 1 | Alice | true |\n" + "| 2 | Bob | false |\n";
        assertEquals(expected, writer.getResult());
    }

    @Test
    void null_value_produces_empty_cell()
    {
        writer.initResult(new String[] { "col" });
        writer.startRow();
        writer.writeNull();
        writer.endRow();

        assertEquals("| col |\n| --- |\n|  |\n", writer.getResult());
    }

    @Test
    void empty_result_set_has_only_header()
    {
        writer.initResult(new String[] { "a", "b" });

        assertEquals("| a | b |\n| --- | --- |\n", writer.getResult());
    }

    @Test
    void multiple_result_sets_separated_by_blank_line()
    {
        writer.initResult(new String[] { "x" });
        writer.startRow();
        writer.writeValue(1);
        writer.endRow();

        writer.initResult(new String[] { "y" });
        writer.startRow();
        writer.writeValue(2);
        writer.endRow();

        String result = writer.getResult();
        // Two tables separated by blank line
        assertEquals("| x |\n| --- |\n| 1 |\n\n| y |\n| --- |\n| 2 |\n", result);
    }

    @Test
    void pipe_in_value_is_escaped()
    {
        writer.initResult(new String[] { "col" });
        writer.startRow();
        writer.writeValue("a|b");
        writer.endRow();

        assertEquals("| col |\n| --- |\n| a\\|b |\n", writer.getResult());
    }

    @Test
    void nested_object_shows_placeholder()
    {
        writer.initResult(new String[] { "data" });
        writer.startRow();
        writer.startObject(); // root row wrapper (engine always emits this)
        writer.writeFieldName("data");
        writer.startObject(); // nested object value => show placeholder
        writer.writeFieldName("key");
        writer.writeValue("val");
        writer.endObject();
        writer.endObject();
        writer.endRow();

        assertEquals("| data |\n| --- |\n| {...} |\n", writer.getResult());
    }

    @Test
    void nested_array_shows_placeholder()
    {
        writer.initResult(new String[] { "items" });
        writer.startRow();
        writer.startObject(); // root row wrapper (engine always emits this)
        writer.writeFieldName("items");
        writer.startArray(); // nested array value => show placeholder
        writer.writeValue(1);
        writer.writeValue(2);
        writer.endArray();
        writer.endObject();
        writer.endRow();

        assertEquals("| items |\n| --- |\n| [...] |\n", writer.getResult());
    }

    @Test
    void numeric_types_written_correctly()
    {
        writer.initResult(new String[] { "l", "f", "d" });
        writer.startRow();
        writer.writeLong(100L);
        writer.writeFloat(1.5f);
        writer.writeDouble(3.14);
        writer.endRow();

        assertEquals("| l | f | d |\n| --- | --- | --- |\n| 100 | 1.5 | 3.14 |\n", writer.getResult());
    }

    @Test
    void delayed_header_single_row()
    {
        // Engine does not provide column names in initResult — they arrive via writeFieldName
        writer.initResult(new String[0]);
        writer.startRow();
        writer.startObject();
        writer.writeFieldName("id");
        writer.writeInt(1);
        writer.writeFieldName("name");
        writer.writeValue("Alice");
        writer.endObject();
        writer.endRow();

        assertEquals("| id | name |\n| --- | --- |\n| 1 | Alice |\n", writer.getResult());
    }

    @Test
    void delayed_header_multiple_rows()
    {
        writer.initResult(new String[0]);
        writer.startRow();
        writer.startObject();
        writer.writeFieldName("a");
        writer.writeInt(1);
        writer.writeFieldName("b");
        writer.writeInt(2);
        writer.endObject();
        writer.endRow();

        // Second row: headers already written, writeFieldName is a no-op
        writer.startRow();
        writer.startObject();
        writer.writeFieldName("a");
        writer.writeInt(3);
        writer.writeFieldName("b");
        writer.writeInt(4);
        writer.endObject();
        writer.endRow();

        assertEquals("| a | b |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |\n", writer.getResult());
    }
}
