package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import com.queryeer.api.extensions.catalog.ICompletionProvider;

import se.kuseman.payloadbuilder.api.QualifiedName;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.api.execution.UTF8String;
import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnectionsModel.Connection;

import schemacrawler.inclusionrule.RegularExpressionInclusionRule;
import schemacrawler.schema.Catalog;
import schemacrawler.schema.Table;
import schemacrawler.schema.TableConstraintType;
import schemacrawler.schema.View;
import schemacrawler.schemacrawler.InfoLevel;
import schemacrawler.schemacrawler.LimitOptionsBuilder;
import schemacrawler.schemacrawler.LoadOptionsBuilder;
import schemacrawler.schemacrawler.SchemaCrawlerOptions;
import schemacrawler.schemacrawler.SchemaCrawlerOptionsBuilder;
import schemacrawler.tools.databaseconnector.DatabaseConnector;
import schemacrawler.tools.databaseconnector.DatabaseConnectorRegistry;
import schemacrawler.tools.utility.SchemaCrawlerUtility;
import us.fatehi.utility.datasource.DatabaseConnectionSource;
import us.fatehi.utility.datasource.DatabaseConnectionSourceBuilder;
import us.fatehi.utility.datasource.MultiUseUserCredentials;

/** Completion provider for JDBC */
class JdbcCompletionProvider implements ICompletionProvider
{
    private static final String TABLE_TEMPLATE = readResource("/se/kuseman/payloadbuilder/catalog/jdbc/templates/Table.html");
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcCompletionProvider.class);
    private final TemplateEngine templateEngine;
    private final JdbcConnectionsModel model;

    JdbcCompletionProvider(JdbcConnectionsModel model)
    {
        this.model = requireNonNull(model, "model");
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(new StringTemplateResolver());
    }

    @Override
    public Object getTableMetaCacheKey(IQuerySession session, String catalogAlias)
    {
        String url = session.getCatalogProperty(catalogAlias, JdbcCatalog.URL)
                .valueAsString(0);
        String database = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);
        String schema = session.getCatalogProperty(catalogAlias, JdbcCatalog.SCHEMA)
                .valueAsString(0);

        return new TableCacheKey(url, schema, database);
    }

    @Override
    public String getDescription(IQuerySession session, String catalogAlias)
    {
        String database = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);
        String schema = session.getCatalogProperty(catalogAlias, JdbcCatalog.SCHEMA)
                .valueAsString(0);

        Connection connection = model.findConnection(session, catalogAlias);
        if (connection != null)
        {
            return connection.getName() + "#" + defaultString(database, schema);
        }

        return JdbcCatalogExtension.TITLE + "#" + defaultString(database, schema);
    }

    @Override
    public boolean enabled(IQuerySession session, String catalogAlias)
    {
        String url = session.getCatalogProperty(catalogAlias, JdbcCatalog.URL)
                .valueAsString(0);
        String driverClassName = session.getCatalogProperty(catalogAlias, JdbcCatalog.DRIVER_CLASSNAME)
                .valueAsString(0);
        String database = session.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);
        String schema = session.getCatalogProperty(catalogAlias, JdbcCatalog.SCHEMA)
                .valueAsString(0);
        String username = session.getCatalogProperty(catalogAlias, JdbcCatalog.USERNAME)
                .valueAsString(0);
        Object password = session.getCatalogProperty(catalogAlias, JdbcCatalog.PASSWORD)
                .valueAsObject(0);

        return !(isBlank(url)
                || isBlank(driverClassName)
                || (isBlank(database)
                        && isBlank(schema))
                || isBlank(username)
                || password == null);
    }

    @Override
    public List<TableMeta> getTableCompletionMeta(IQuerySession querySession, String catalogAlias)
    {
        String database = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.DATABASE)
                .valueAsString(0);
        String schema = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.SCHEMA)
                .valueAsString(0);
        String url = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.URL)
                .valueAsString(0);
        final String username = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.USERNAME)
                .valueAsString(0);
        final String password = getPassword(querySession, catalogAlias);

        try
        {
            DatabaseConnectorRegistry registry = DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
            DatabaseConnector dbConnector = registry.findDatabaseConnectorFromUrl(url);

            LimitOptionsBuilder limitOptionsBuilder = LimitOptionsBuilder.builder();
            if (!isBlank(database))
            {
                limitOptionsBuilder.includeSchemas(new RegularExpressionInclusionRule(database + ".*"));
            }
            if (!isBlank(schema))
            {
                // For schemas we don't wildcard search we want only the provided schema
                limitOptionsBuilder.includeSchemas(new RegularExpressionInclusionRule(schema));
            }

            SchemaCrawlerOptions crawlOptions = SchemaCrawlerOptionsBuilder.newSchemaCrawlerOptions()
                    .withLimitOptions(limitOptionsBuilder.toOptions())
                    .withLoadOptions(LoadOptionsBuilder.builder()
                            .withInfoLevel(getInfoLevel(dbConnector))
                            .toOptions());

            if (!isBlank(database))
            {
                url += ";databaseName=${database}";
            }

            DatabaseConnectionSource ds = DatabaseConnectionSourceBuilder.builder(url)
                    .withDatabase(database)
                    .withUserCredentials(new MultiUseUserCredentials(username, password))
                    .build();

            final Catalog catalog = SchemaCrawlerUtility.getCatalog(ds, crawlOptions);

            return catalog.getTables()
                    .stream()
                    .map(t ->
                    {
                        List<ColumnMeta> columns = t.getColumns()
                                .stream()
                                .map(c -> new ColumnMeta(QualifiedName.of(c.getName()), "", ""))
                                .collect(toList());

                        String description = "";
                        if (!(t instanceof View))
                        {
                            description = generateTableDescription(t);
                        }

                        return new TableMeta(QualifiedName.of(t.getSchema()
                                .getName(), t.getName()), description, description, columns);
                    })
                    .collect(toList());

        }
        catch (Exception e)
        {
            throw new RuntimeException("Error crawling schema", e);
        }
    }

    record TableCacheKey(String url, String schema, String database)
    {
    }

    private InfoLevel getInfoLevel(DatabaseConnector dbConnector)
    {
        return switch (dbConnector.getDatabaseServerType()
                .getDatabaseSystemName()
                .toLowerCase())
        {
            case "sqlserver" -> InfoLevel.detailed;
            case "oracle" -> InfoLevel.standard;
            default -> InfoLevel.standard;
        };
    }

    private String generateTableDescription(Table table)
    {
        Context context = new Context();
        context.setVariable("table", table);

        try
        {
            StringWriter writer = new StringWriter();
            templateEngine.process(TABLE_TEMPLATE, context, writer);
            return writer.toString();
        }
        catch (Exception e)
        {
            LOGGER.error("Error generating table description for table: " + table, e);
            return "";
        }
    }

    private String getPassword(IQuerySession session, String catalogAlias)
    {
        Object obj = session.getCatalogProperty(catalogAlias, JdbcCatalog.PASSWORD)
                .valueAsObject(0);
        if (obj instanceof String
                || obj instanceof UTF8String)
        {
            return String.valueOf(obj);
        }
        else if (obj instanceof char[])
        {
            return new String((char[]) obj);
        }
        return null;
    }

    private static String readResource(String resource)
    {
        try
        {
            return IOUtils.toString(JdbcCompletionProvider.class.getResourceAsStream(resource), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error loading resource: " + resource, e);
        }
    }

    /** Method called from template to check if there are any table constraints to print */
    public static boolean hasTableConstrains(Table table)
    {
        return table.getTableConstraints()
                .stream()
                .anyMatch(t -> t.getType() == TableConstraintType.check
                        || t.getType() == TableConstraintType.unique
                        || t.getType() == TableConstraintType.unknown);
    }
}
