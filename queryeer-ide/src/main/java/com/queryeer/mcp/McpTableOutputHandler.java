package com.queryeer.mcp;

import static com.queryeer.mcp.McpToolParameter.ParameterType.INTEGER;
import static com.queryeer.mcp.McpToolParameter.ParameterType.STRING;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.api.extensions.output.table.ITableOutputComponent.Table;
import com.queryeer.api.service.IQueryFileProvider;

/** Handler for interal tools for table output. */
class McpTableOutputHandler
{
    private static final String RESULTSET_META = "__resultset_meta";
    private static final String RESULTSET_SAMPLE_ROWS = "__resultset_sample_rows";
    private static final String RESULTSET_GET_ROWS = "__resultset_get_rows";
    static final String PARAM_RESULTSET_INDEX = "resultsetIndex";
    private static final String PARAM_ROWS = "rows";
    private static final String PARAM_OFFSET = "offset";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_COLUMNS = "columns";

    private final IQueryFileProvider queryFileProvider;

    McpTableOutputHandler(IQueryFileProvider queryFileProvider)
    {
        this.queryFileProvider = queryFileProvider;
    }

    /** Get table output tools definitions. */
    List<McpTool> getTableOutputTools()
    {
        record TableTool(String name, String description, List<String> params, List<McpToolParameter.ParameterType> paramTypes, List<String> paramDescs)
        {
        }

        String resultSetIndexDescription = "Index of resultset. Zero based.";
        String columnsDescription = "Comma separated list of columns. * for all";

        //@formatter:off
        List<TableTool> tools = List.of(
                new TableTool(RESULTSET_META, "List meta data about current resultsets in current query. Number of resultsets, their columns, row counts. etc.", List.of(), List.of(), List.of()),
                new TableTool(RESULTSET_SAMPLE_ROWS, "Sample rows from a resultset.",
                        List.of(PARAM_RESULTSET_INDEX, PARAM_COLUMNS, PARAM_ROWS),
                        List.of(INTEGER, STRING, INTEGER),
                        List.of(resultSetIndexDescription, columnsDescription, "Number of rows to sample")),
                new TableTool(RESULTSET_GET_ROWS, "Get rows from resultset.",
                        List.of(PARAM_RESULTSET_INDEX, PARAM_COLUMNS, PARAM_OFFSET, PARAM_LIMIT),
                        List.of(INTEGER, STRING, INTEGER, INTEGER),
                        List.of(resultSetIndexDescription, columnsDescription, "Offset of first row to fetch. Zero based.", "Limit of rows to fetch"))
                );
        //@formatter:on
        return tools.stream()
                .map(t ->
                {
                    McpTool tool = new McpTool();
                    tool.setName(t.name);
                    tool.setDescription(t.description);

                    int size = t.params.size();
                    List<McpToolParameter> parameters = new ArrayList<>();
                    tool.setParameters(parameters);
                    for (int i = 0; i < size; i++)
                    {
                        McpToolParameter p = new McpToolParameter();
                        p.setName(t.params.get(i));
                        p.setDescription(t.paramDescs.get(i));
                        p.setType(t.paramTypes.get(i));
                        parameters.add(p);
                    }
                    return tool;
                })
                .toList();
    }

    String handleToolCall(String name, Map<String, Object> arguments) throws Exception
    {
        if (RESULTSET_META.equalsIgnoreCase(name))
        {
            ITableOutputComponent outputComponent = getTableOutputComponent(arguments);
            int size = outputComponent.getTables()
                    .size();

            /*
             * @formatter:off
             * {
             *   "tableCount": 4,
             *   "tables": [
             *     {
             *       "rowCount": 123,
             *       "columns": ["col1", "col2" ],
             *       "types": [ "String", "String" ]
             *     }
             *   ]
             * }
             * @formatter:on
             */

            return McpHttpHandler.MAPPER.writeValueAsString(Map.of("resultsetCount", size, "resultsets", IntStream.range(0, size)
                    .mapToObj(i ->
                    {
                        ITableOutputComponent.Table table = outputComponent.getTables()
                                .get(i);
                        return Map.of("rowCount", table.getRowCount(), "columns", table.getColumns(), "types", table.getTypes()
                                .stream()
                                .map(this::columnTypeToJsonSchema)
                                .toList());
                    })
                    .toList()));
        }
        else if (RESULTSET_SAMPLE_ROWS.equalsIgnoreCase(name))
        {
            Table table = getTable(arguments);
            int sampleSize = Math.min(table.getRowCount(), Math.min(20, ((Number) arguments.get(PARAM_ROWS)).intValue()));
            return buildRowsData(table, 0, sampleSize, arguments);
        }
        else if (RESULTSET_GET_ROWS.equalsIgnoreCase(name))
        {
            Table table = getTable(arguments);
            int offset = ((Number) arguments.get(PARAM_OFFSET)).intValue();
            int limit = ((Number) arguments.get(PARAM_LIMIT)).intValue();
            return buildRowsData(table, offset, limit, arguments);
        }

        return null;
    }

    private String buildRowsData(Table table, int offset, int limit, Map<String, Object> arguments) throws Exception
    {
        /*
         * @formatter:off
         * {
         *   "columns": ["col1", "col2" ],
         *   "rows": [
         *     [a,b,c],
         *     [d,e,f]
         *   ]
         * }
         * @formatter:on
         */

        /*
         * @formatter:on
         * offset,limit,rowCount => limit
         * 0, 10, 1 => 1
         * 0, 10, 20 => 10
         * 10, 10, 19 => 9
         * @formatter:off
         */
        if (offset + limit > table.getRowCount())
        {
            limit = table.getRowCount() - offset;
        }

        String columnsString = (String) arguments.get(PARAM_COLUMNS);
        List<String> columns;
        if ("*".equals(columnsString))
        {
            columns = table.getColumns();
        }
        else
        {
            columns = List.of(StringUtils.split(columnsString, ','));
        }

        int columnSize = columns.size();
        int[] columnIndices = new int[columnSize];
        for (int i = 0; i < columns.size(); i++)
        {
            columnIndices[i] = table.getColumns()
                    .indexOf(columns.get(i));
            if (columnIndices[i] == -1)
            {
                throw new IllegalArgumentException("Column with name: " + columns.get(i) + " does not exists");
            }
        }

        //@formatter:off
        List<Class<?>> types = table.getTypes();
        return McpHttpHandler.MAPPER.writeValueAsString(Map.of(
                "columns", columns,
                "rows", IntStream.range(offset, offset + limit)
                .mapToObj(i ->
                {
                    Object[] row = new Object[columnSize];
                    for (int j = 0; j < columnSize; j++)
                    {
                        Object value = table.getValueAt(i, columnIndices[j]);
                        row[j] = convert(types.get(columnIndices[j]), value);
                    }
                    return row;
                })
                .toList()));
        //@formatter:on
    }

    private ITableOutputComponent getTableOutputComponent(Map<String, Object> arguments)
    {
        IQueryFile queryFile = queryFileProvider.getCurrentFile();
        if (queryFile == null)
        {
            throw new IllegalArgumentException("No current query file is set.");
        }

        ITableOutputComponent outputComponent = queryFile.getOutputComponent(ITableOutputComponent.class);
        if (outputComponent == null)
        {
            throw new IllegalArgumentException("No table outputcomponent found for current query file");
        }
        return outputComponent;
    }

    private ITableOutputComponent.Table getTable(Map<String, Object> arguments)
    {
        ITableOutputComponent outputComponent = getTableOutputComponent(arguments);
        int resultSetIndex = ((Number) arguments.get(PARAM_RESULTSET_INDEX)).intValue();
        List<? extends Table> tables = outputComponent.getTables();
        if (resultSetIndex < 0
                || resultSetIndex >= tables.size())
        {
            throw new IllegalArgumentException("resultSetIndex: " + resultSetIndex + " is out of range.");
        }
        return tables.get(resultSetIndex);
    }

    private Object convert(Class<?> type, Object value)
    {
        if (type == Integer.class
                || type == int.class)
        {
            return value;
        }

        return value == null ? null
                : String.valueOf(value);
    }

    private String columnTypeToJsonSchema(Class<?> clazz)
    {
        if (clazz == Integer.class
                || clazz == int.class)
        {
            return "integer";
        }
        return "string";
    }
}
