package se.kuseman.payloadbuilder.catalog.jdbc;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.api.extensions.engine.IMcpHandler;

import se.kuseman.payloadbuilder.catalog.jdbc.NamedParameterParser.ParsedQuery;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;

class JdbcMcpHandler implements IMcpHandler
{
    private final JdbcQueryEngine queryEngine;
    private final JdbcConnectionsModel connectionsModel;

    JdbcMcpHandler(JdbcQueryEngine queryEngine, JdbcConnectionsModel connectionsModel)
    {
        this.queryEngine = queryEngine;
        this.connectionsModel = connectionsModel;
    }

    @Override
    public String getParameterSyntaxHint()
    {
        return "Use :paramName syntax to safely inject parameter values (JDBC named parameters).";
    }

    @Override
    public Component getMcpConnectionComponent(Map<String, Object> config, java.util.function.Consumer<Boolean> dirtyConsumer)
    {
        return new JdbcMcpConnectionComponent(connectionsModel, config, dirtyConsumer);
    }

    @Override
    public McpResult execute(Map<String, Object> mcpConnectionConfig, String query, Map<String, Object> parameters) throws Exception
    {
        String connectionName = (String) mcpConnectionConfig.get(JdbcMcpConnectionComponent.KEY_CONNECTION);
        String database = (String) mcpConnectionConfig.get(JdbcMcpConnectionComponent.KEY_DATABASE);

        JdbcConnection connection = connectionsModel.getConnections()
                .stream()
                .filter(c -> c.getName()
                        .equals(connectionName))
                .findFirst()
                .orElse(null);

        if (connection == null
                || !connection.isEnabled())
        {
            throw new IllegalArgumentException("Connection with name: " + connectionName + " not found or is not active");
        }
        else if (isBlank(database))
        {
            throw new IllegalArgumentException("Database not set");
        }

        JdbcDialect dialect = queryEngine.dialectProvider.getDialect(connection.getJdbcURL());
        ParsedQuery parsedQuery = NamedParameterParser.parse(query, parameters);

        try (Connection con = connectionsModel.createConnection(connection); PreparedStatement stm = con.prepareStatement(parsedQuery.query()))
        {
            if (dialect.usesSchemaAsDatabase())
            {
                con.setSchema(database);
            }
            else
            {
                con.setCatalog(database);
            }

            int index = 1;
            for (Object value : parsedQuery.values())
            {
                stm.setObject(index++, value);
            }

            List<String> columns = null;
            List<Object[]> rows = new ArrayList<>();

            boolean first = true;
            while (true)
            {
                AtomicReference<SQLException> exception = new AtomicReference<>();
                try (ResultSet rs = JdbcUtils.getNextResultSet(e -> exception.set(e), new StringWriter(), stm, null, first))
                {
                    first = false;
                    // We're done!
                    if (rs == null)
                    {
                        break;
                    }

                    int columnCount = rs.getMetaData()
                            .getColumnCount();
                    Pair<int[], String[]> columnsMeta = queryEngine.getColumnsMeta(rs);
                    List<String> current = List.of(columnsMeta.getValue());
                    if (columns == null)
                    {
                        columns = current;
                    }
                    else if (!columns.equals(current))
                    {
                        throw new IllegalArgumentException("All result set columns must be equal");
                    }

                    while (rs.next())
                    {
                        Object[] row = new Object[columnCount];
                        rows.add(row);
                        for (int i = 0; i < columnCount; i++)
                        {
                            row[i] = dialect.getJdbcValue(rs, i + 1, columnsMeta.getKey()[i]);
                        }
                    }
                }

                if (exception.get() != null)
                {
                    throw exception.get();
                }
            }

            return new McpResult(columns, rows);
        }
    }
}
