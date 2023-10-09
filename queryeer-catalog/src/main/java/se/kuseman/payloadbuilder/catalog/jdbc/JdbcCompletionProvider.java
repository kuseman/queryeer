package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.queryeer.api.extensions.payloadbuilder.ICompletionProvider;

import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DatabaseProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;

/** Completion provider for JDBC payloadbuilder */
class JdbcCompletionProvider implements ICompletionProvider
{
    private final JdbcConnectionsModel model;
    private final CatalogCrawlService crawlService;
    private final DatabaseProvider databaseProvider;

    JdbcCompletionProvider(JdbcConnectionsModel model, CatalogCrawlService crawlService, DatabaseProvider databaseProvider)
    {
        this.model = requireNonNull(model, "model");
        this.crawlService = requireNonNull(crawlService, "crawlService");
        this.databaseProvider = requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public Object getTableMetaCacheKey(IQuerySession session, String catalogAlias)
    {
        return null;
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
        JdbcDatabase jdbcDatabase = databaseProvider.getDatabase(jdbcConnection.getJdbcURL());

        String database = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);

        IConnectionState state = new IConnectionState()
        {
            @Override
            public JdbcDatabase getJdbcDatabase()
            {
                return jdbcDatabase;
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
        };

        Catalog catalog = crawlService.getCatalog(state, database);
        if (catalog == null)
        {
            return emptyList();
        }
        return emptyList();
    }
}
