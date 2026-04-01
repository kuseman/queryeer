package se.kuseman.payloadbuilder.catalog.jdbc;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.queryeer.api.extensions.engine.IMcpHandler;

import se.kuseman.payloadbuilder.api.OutputWriter;
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
    public void execute(Map<String, Object> mcpConnectionConfig, String query, Map<String, Object> parameters, OutputWriter outputWriter) throws Exception
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

                    queryEngine.writeResultSet(connection, dialect, con, rs, () -> false, () -> true, outputWriter);
                }

                if (exception.get() != null)
                {
                    throw exception.get();
                }
            }
        }
    }
}
