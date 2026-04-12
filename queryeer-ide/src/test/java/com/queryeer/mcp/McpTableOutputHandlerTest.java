package com.queryeer.mcp;

import static com.queryeer.mcp.McpTableOutputHandler.PARAM_RESULTSET_INDEX;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.api.service.IQueryFileProvider;

/** Tests for {@link McpTableOutputHandler} */
class McpTableOutputHandlerTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IQueryFileProvider queryFileProvider;
    private McpTableOutputHandler handler;

    @BeforeEach
    void setup()
    {
        queryFileProvider = mock(IQueryFileProvider.class);
        handler = new McpTableOutputHandler(queryFileProvider);
    }

    // ---- helpers ----

    /** Simple array-backed Table implementation for tests. */
    static class TableData implements ITableOutputComponent.Table
    {
        private final List<String> columns;
        private final List<Class<?>> types;
        private final Object[][] data;

        TableData(List<String> columns, List<Class<?>> types, Object[][] data)
        {
            this.columns = columns;
            this.types = types;
            this.data = data;
        }

        @Override
        public int getRowCount()
        {
            return data.length;
        }

        @Override
        public List<String> getColumns()
        {
            return columns;
        }

        @Override
        public List<Class<?>> getTypes()
        {
            return types;
        }

        @Override
        public Object getValueAt(int row, int column)
        {
            return data[row][column];
        }
    }

    private void registerQueryFile(ITableOutputComponent.Table... tables)
    {
        ITableOutputComponent comp = mock(ITableOutputComponent.class);
        when(comp.getTables()).thenReturn(List.of(tables));
        IQueryFile queryFile = mock(IQueryFile.class);
        when(queryFile.getOutputComponent(ITableOutputComponent.class)).thenReturn(comp);
        when(queryFileProvider.getCurrentFile()).thenReturn(queryFile);
    }

    private void registerQueryFileNoTableComponent(int id)
    {
        IQueryFile queryFile = mock(IQueryFile.class);
        when(queryFile.getOutputComponent(ITableOutputComponent.class)).thenReturn(null);
        when(queryFileProvider.getCurrentFile()).thenReturn(queryFile);
    }

    // ---- getTableOutputTools ----

    @Test
    void get_table_output_tools_returns_three_tools()
    {
        List<McpTool> tools = handler.getTableOutputTools();
        assertEquals(3, tools.size());
        assertTrue(tools.stream()
                .anyMatch(t -> t.getName()
                        .equals("__resultset_meta")));
        assertTrue(tools.stream()
                .anyMatch(t -> t.getName()
                        .equals("__resultset_sample_rows")));
        assertTrue(tools.stream()
                .anyMatch(t -> t.getName()
                        .equals("__resultset_get_rows")));
    }

    @Test
    void all_table_tools_are_active()
    {
        handler.getTableOutputTools()
                .forEach(t -> assertTrue(t.isActive()));
    }

    @Test
    void table_meta_tool_has_no_parameters()
    {
        McpTool meta = handler.getTableOutputTools()
                .stream()
                .filter(t -> t.getName()
                        .equals("__resultset_meta"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, meta.getParameters()
                .size());
    }

    @Test
    void table_get_rows_tool_has_four_parameters()
    {
        McpTool getRows = handler.getTableOutputTools()
                .stream()
                .filter(t -> t.getName()
                        .equals("__resultset_get_rows"))
                .findFirst()
                .orElseThrow();
        assertEquals(4, getRows.getParameters()
                .size());
    }

    // ---- __resultset_meta ----

    @Test
    void table_meta_returns_resultset_count_and_column_info() throws Exception
    {
        registerQueryFile(new TableData(List.of("name", "age"), List.of(String.class, Integer.class), new Object[42][2]));

        String json = handler.handleToolCall("__resultset_meta", Map.of());
        JsonNode root = MAPPER.readTree(json);

        assertEquals(1, root.path("resultsetCount")
                .asInt());
        JsonNode tableNode = root.path("resultsets")
                .get(0);
        assertEquals(42, tableNode.path("rowCount")
                .asInt());
        assertEquals("name", tableNode.path("columns")
                .get(0)
                .asText());
        assertEquals("age", tableNode.path("columns")
                .get(1)
                .asText());
        assertEquals("string", tableNode.path("types")
                .get(0)
                .asText());
        assertEquals("integer", tableNode.path("types")
                .get(1)
                .asText());
    }

    @Test
    void table_meta_multiple_tables() throws Exception
    {
        registerQueryFile(new TableData(List.of("id"), List.of(Integer.class), new Object[10][1]), new TableData(List.of("val"), List.of(String.class), new Object[5][1]));

        String json = handler.handleToolCall("__resultset_meta", Map.of());
        assertEquals(2, MAPPER.readTree(json)
                .path("resultsetCount")
                .asInt());
    }

    @Test
    void table_meta_unknown_query_file_throws()
    {
        when(queryFileProvider.getCurrentFile()).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> handler.handleToolCall("__resultset_meta", Map.of()));
    }

    @Test
    void table_meta_no_table_component_throws()
    {
        registerQueryFileNoTableComponent(1);
        assertThrows(IllegalArgumentException.class, () -> handler.handleToolCall("__resultset_meta", Map.of()));
    }

    @Test
    void unknown_tool_name_returns_null() throws Exception
    {
        assertNull(handler.handleToolCall("__unknown_tool", Map.of()));
    }

    // ---- __resultset_sample_rows ----

    @Test
    void table_sample_rows_returns_requested_count() throws Exception
    {
        Object[][] data = { { "Alice" }, { "Bob" }, { "Carol" }, { "Dave" }, { "Eve" } };
        registerQueryFile(new TableData(List.of("name"), List.of(String.class), data));

        String json = handler.handleToolCall("__resultset_sample_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "*", "rows", 3));
        assertEquals(3, MAPPER.readTree(json)
                .path("rows")
                .size());
    }

    @Test
    void table_sample_rows_capped_at_20_when_requesting_more() throws Exception
    {
        Object[][] data = new Object[50][1];
        for (int i = 0; i < 50; i++)
        {
            data[i][0] = "row" + i;
        }
        registerQueryFile(new TableData(List.of("val"), List.of(String.class), data));

        String json = handler.handleToolCall("__resultset_sample_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "*", "rows", 30));
        assertEquals(20, MAPPER.readTree(json)
                .path("rows")
                .size());
    }

    @Test
    void table_sample_rows_capped_by_table_size_when_smaller_than_20() throws Exception
    {
        Object[][] data = { { "a" }, { "b" }, { "c" } };
        registerQueryFile(new TableData(List.of("v"), List.of(String.class), data));

        String json = handler.handleToolCall("__resultset_sample_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "*", "rows", 20));
        assertEquals(3, MAPPER.readTree(json)
                .path("rows")
                .size());
    }

    @Test
    void table_sample_rows_fewer_requested_than_20_returns_that_count() throws Exception
    {
        // Regression for Math.max bug — requesting 5 must NOT return 20
        Object[][] data = new Object[50][1];
        for (int i = 0; i < 50; i++)
        {
            data[i][0] = "row" + i;
        }
        registerQueryFile(new TableData(List.of("val"), List.of(String.class), data));

        String json = handler.handleToolCall("__resultset_sample_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "*", "rows", 5));
        assertEquals(5, MAPPER.readTree(json)
                .path("rows")
                .size());
    }

    // ---- __resultset_get_rows ----

    @Test
    void table_get_rows_returns_correct_range() throws Exception
    {
        Object[][] data = new Object[10][1];
        for (int i = 0; i < 10; i++)
        {
            data[i][0] = "row" + i;
        }
        registerQueryFile(new TableData(List.of("val"), List.of(String.class), data));

        String json = handler.handleToolCall("__resultset_get_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "*", "offset", 3, "limit", 4));
        JsonNode rows = MAPPER.readTree(json)
                .path("rows");
        assertEquals(4, rows.size());
        assertEquals("row3", rows.get(0)
                .get(0)
                .asText());
        assertEquals("row6", rows.get(3)
                .get(0)
                .asText());
    }

    @Test
    void table_get_rows_exact_end_boundary_succeeds() throws Exception
    {
        // Regression for >= bug: offset(0) + limit(10) == rowCount(10) must succeed
        Object[][] data = new Object[10][1];
        for (int i = 0; i < 10; i++)
        {
            data[i][0] = "r" + i;
        }
        registerQueryFile(new TableData(List.of("v"), List.of(String.class), data));

        assertDoesNotThrow(() -> handler.handleToolCall("__resultset_get_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "*", "offset", 0, "limit", 10)));
    }

    @Test
    void table_get_rows_out_of_bounds_limits_to_row_count() throws Exception
    {
        registerQueryFile(new TableData(List.of("v"), List.of(String.class), new Object[10][1]));

        String json = handler.handleToolCall("__resultset_get_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "*", "offset", 5, "limit", 10));
        JsonNode root = MAPPER.readTree(json);
        JsonNode columns = root.path("columns");
        assertEquals(1, columns.size());
        JsonNode rows = root.path("rows");
        assertEquals(5, rows.size());
    }

    @Test
    void table_get_rows_all_columns_star_returns_all_column_headers() throws Exception
    {
        Object[][] data = { { "Alice", 30 } };
        registerQueryFile(new TableData(List.of("name", "age"), List.of(String.class, Integer.class), data));

        String json = handler.handleToolCall("__resultset_get_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "*", "offset", 0, "limit", 1));
        JsonNode root = MAPPER.readTree(json);
        JsonNode columns = root.path("columns");
        assertEquals(2, columns.size());
        assertEquals("name", columns.get(0)
                .asText());
        assertEquals("age", columns.get(1)
                .asText());
    }

    @Test
    void table_get_rows_specific_columns_returns_only_those_headers() throws Exception
    {
        // Regression: "columns" in response must match requested columns, not all table columns
        Object[][] data = { { "Alice", 30, "NYC" }, { "Bob", 25, "LA" } };
        registerQueryFile(new TableData(List.of("name", "age", "city"), List.of(String.class, Integer.class, String.class), data));

        String json = handler.handleToolCall("__resultset_get_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "age,city", "offset", 0, "limit", 2));
        JsonNode root = MAPPER.readTree(json);

        JsonNode columns = root.path("columns");
        assertEquals(2, columns.size());
        assertEquals("age", columns.get(0)
                .asText());
        assertEquals("city", columns.get(1)
                .asText());

        JsonNode rows = root.path("rows");
        assertEquals(2, rows.size());
        // Each row must have exactly 2 values matching the requested columns
        assertEquals(2, rows.get(0)
                .size());
    }

    @Test
    void table_get_rows_specific_columns_use_correct_types() throws Exception
    {
        // Regression for types.get(j) bug: when requesting "age" (col index 1),
        // the Integer type must be used, not the String type from col index 0
        Object[][] data = { { "Alice", 30 }, { "Bob", 25 } };
        registerQueryFile(new TableData(List.of("name", "age"), List.of(String.class, Integer.class), data));

        String json = handler.handleToolCall("__resultset_get_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "age", "offset", 0, "limit", 2));
        JsonNode root = MAPPER.readTree(json);
        JsonNode rows = root.path("rows");

        // age is Integer, so it must serialize as a JSON number, not a string
        assertTrue(rows.get(0)
                .get(0)
                .isNumber(),
                "age value should be a JSON number but was: " + rows.get(0)
                        .get(0));
        assertEquals(30, rows.get(0)
                .get(0)
                .asInt());
        assertEquals(25, rows.get(1)
                .get(0)
                .asInt());
    }

    @Test
    void table_get_rows_null_value_serializes_as_json_null() throws Exception
    {
        Object[][] data = { { null, "present" } };
        registerQueryFile(new TableData(List.of("a", "b"), List.of(String.class, String.class), data));

        String json = handler.handleToolCall("__resultset_get_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "*", "offset", 0, "limit", 1));
        JsonNode rows = MAPPER.readTree(json)
                .path("rows");
        assertTrue(rows.get(0)
                .get(0)
                .isNull());
        assertEquals("present", rows.get(0)
                .get(1)
                .asText());
    }

    @Test
    void table_get_rows_unknown_column_throws()
    {
        registerQueryFile(new TableData(List.of("name"), List.of(String.class), new Object[5][1]));

        assertThrows(IllegalArgumentException.class, () -> handler.handleToolCall("__resultset_get_rows", Map.of(PARAM_RESULTSET_INDEX, 0, "columns", "nonexistent", "offset", 0, "limit", 3)));
    }

    @Test
    void table_get_rows_invalid_table_index_throws()
    {
        registerQueryFile(new TableData(List.of("v"), List.of(String.class), new Object[5][1]));

        assertThrows(IllegalArgumentException.class, () -> handler.handleToolCall("__resultset_get_rows", Map.of(PARAM_RESULTSET_INDEX, 5, "columns", "*", "offset", 0, "limit", 5)));
    }

    @Test
    void table_get_rows_negative_table_index_throws()
    {
        registerQueryFile(new TableData(List.of("v"), List.of(String.class), new Object[5][1]));

        assertThrows(IllegalArgumentException.class, () -> handler.handleToolCall("__resultset_get_rows", Map.of(PARAM_RESULTSET_INDEX, -1, "columns", "*", "offset", 0, "limit", 5)));
    }
}
