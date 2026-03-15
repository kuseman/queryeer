package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.queryeer.api.extensions.payloadbuilder.ICompletionProvider;

import se.kuseman.payloadbuilder.api.QualifiedName;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialectProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Column;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;

/** Completion provider for JDBC payloadbuilder */
class JdbcCompletionProvider implements ICompletionProvider
{
    private final JdbcConnectionsModel model;
    private final CatalogCrawlService crawlService;
    private final JdbcDialectProvider dialectProvider;

    JdbcCompletionProvider(JdbcConnectionsModel model, CatalogCrawlService crawlService, JdbcDialectProvider dialectProvider)
    {
        this.model = requireNonNull(model, "model");
        this.crawlService = requireNonNull(crawlService, "crawlService");
        this.dialectProvider = requireNonNull(dialectProvider, "dialectProvider");
    }

    @Override
    public Object getTableMetaCacheKey(IQuerySession session, String catalogAlias)
    {
        JdbcConnection connection = model.findConnection(session, catalogAlias);
        if (connection == null)
        {
            return null;
        }
        String database = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);
        return connection.getJdbcURL() + "#" + database;
    }

    @Override
    public String getDescription(IQuerySession session, String catalogAlias)
    {
        JdbcConnection connection = model.findConnection(session, catalogAlias);
        String database = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);
        return connection.getName() + "#" + database;
    }

    @Override
    public boolean enabled(IQuerySession session, String catalogAlias)
    {
        JdbcConnection connection = model.findConnection(session, catalogAlias);
        return connection != null
                && connection.hasCredentials();
    }

    @Override
    public List<TableMeta> getTableCompletionMeta(IQuerySession querySession, String catalogAlias)
    {
        JdbcConnection jdbcConnection = model.findConnection(querySession, catalogAlias);
        JdbcDialect jdbcDialect = dialectProvider.getDialect(jdbcConnection.getJdbcURL());

        String database = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);

        IConnectionState state = new IConnectionState()
        {
            @Override
            public JdbcDialect getJdbcDialect()
            {
                return jdbcDialect;
            }

            @Override
            public JdbcConnection getJdbcConnection()
            {
                return jdbcConnection;
            }

            @Override
            public String getDatabase()
            {
                return database;
            }

            @Override
            public Connection createConnection() throws SQLException
            {
                return model.createConnection(jdbcConnection);
            }

            @Override
            public boolean isIncludeQueryPlan()
            {
                return false;
            }

            @Override
            public boolean isEstimateQueryPlan()
            {
                return false;
            }

            @Override
            public void addChangeListener(Runnable listener)
            {
            }

            @Override
            public void removeChangeListener(Runnable listener)
            {
            }
        };

        Catalog catalog = crawlService.getCatalog(state, database);
        if (catalog == null)
        {
            return emptyList();
        }

        List<TableMeta> result = new ArrayList<>(catalog.getTableSources()
                .size());
        for (TableSource tableSource : catalog.getTableSources())
        {
            QualifiedName tableName = isBlank(tableSource.getSchema()) ? QualifiedName.of(tableSource.getName())
                    : QualifiedName.of(tableSource.getSchema(), tableSource.getName());

            List<ColumnMeta> columns = new ArrayList<>(tableSource.getColumns()
                    .size());
            for (Column column : tableSource.getColumns())
            {
                String description = """
                        <html>
                        <h3>%s</h3>
                        <ul>
                        <li>Type: <strong>%s</strong></li>
                        <li>Nullable: <strong>%s</strong></li>
                        </ul>
                        """.formatted(column.getName(), column.getDefinition(), column.isNullable() ? "yes"
                        : "no");
                columns.add(new ColumnMeta(QualifiedName.of(column.getName()), description, null));
            }

            result.add(new TableMeta(tableName, null, null, columns));
        }
        return result;
    }
}
