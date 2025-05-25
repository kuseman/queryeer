package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.Strings.CI;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.sqlserver.jdbc.SQLServerError;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.editor.TextSelection;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputExtension;
import com.queryeer.api.extensions.output.text.ITextOutputComponent;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.Common;
import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionState;
import se.kuseman.payloadbuilder.catalog.jdbc.Icons;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Column;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Constraint;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKey;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKeyColumn;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Index;
import se.kuseman.payloadbuilder.catalog.jdbc.model.IndexColumn;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Routine;
import se.kuseman.payloadbuilder.catalog.jdbc.model.RoutineParameter;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource.Type;

/** Dialect for Microsoft SQL Server */
class SqlServerDatabase implements JdbcDatabase
{
    private static final String META_DATA_QUERY = Common.readResource("/se/kuseman/payloadbuilder/catalog/jdbc/dialect/SQL_SERVER_CATALOG_QUERY.sql");

    static final String NAME = "sqlserver";
    private static final Logger LOGGER = LoggerFactory.getLogger(SqlServerDatabase.class);
    private final CatalogCrawlService crawlerService;
    private final IEventBus eventBus;
    private final QueryActionsConfigurable queryActionsConfigurable;
    private final TreeNodeSupplier treeNodeSupplier;
    private final Icons icons;
    private final ITemplateService templateService;
    private final IQueryPlanOutputExtension queryPlanOutputExtension;

    public SqlServerDatabase(CatalogCrawlService crawlerService, IEventBus eventBus, Icons icons, QueryActionsConfigurable queryActionsConfigurable, ITemplateService templateService,
            IQueryPlanOutputExtension queryPlanOutputExtension, ITreeConfig treeConfig)
    {
        this.crawlerService = crawlerService;
        this.eventBus = eventBus;
        this.icons = icons;
        this.queryActionsConfigurable = queryActionsConfigurable;
        this.treeNodeSupplier = new SqlServerTreeNodeSupplier(this, eventBus, icons, queryActionsConfigurable, templateService, treeConfig);
        this.templateService = templateService;
        this.queryPlanOutputExtension = queryPlanOutputExtension;
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public TreeNodeSupplier getTreeNodeSupplier()
    {
        return treeNodeSupplier;
    }

    @Override
    public String getSessionKeyword()
    {
        return "SPID";
    }

    @Override
    public String getBatchDelimiter()
    {
        return "GO";
    }

    @Override
    public String getSessionId(Connection connection)
    {
        try (Statement stm = connection.createStatement(); ResultSet rs = stm.executeQuery("SELECT @@SPID"))
        {
            if (rs.next())
            {
                return String.valueOf(rs.getInt(1));
            }
        }
        catch (SQLException e)
        {
            LOGGER.warn("Could not get session id", e);
            return "";
        }
        return "";
    }

    @Override
    public boolean handleSQLException(IQueryFile queryFile, ITextOutputComponent textOutput, SQLException e)
    {
        if (e instanceof SQLServerException sqle)
        {
            SQLServerError serverError = sqle.getSQLServerError();
            if (serverError != null)
            {
                // TODO: be able to mark a single line selection
                String warning = "Error, line: " + serverError.getLineNumber();
                textOutput.appendWarning(warning, TextSelection.EMPTY);
                textOutput.appendWarning(e.getMessage(), TextSelection.EMPTY);

                return true;
            }
        }

        return false;
    }

    @Override
    public ITextEditorDocumentParser getParser(IConnectionState connectionState)
    {
        return new SqlServerDocumentParser(icons, eventBus, queryActionsConfigurable, crawlerService, connectionState, templateService);
    }

    @Override
    public Catalog getCatalog(IConnectionState connectionState, String database)
    {
        try (Connection con = connectionState.createConnection(); PreparedStatement stm = con.prepareStatement(META_DATA_QUERY))
        {
            // CSOFF
            LOGGER.info("Fetching catalog metadata for: " + database);
            long time = System.currentTimeMillis();
            // CSON
            con.setCatalog(database);
            stm.execute();

            // CSOFF
            //@formatter:off
            List<TableSource> tableSources = map(stm,
              rs -> new TableSource(
                      rs.getString("objectCatalog"),
                      rs.getString("objectSchema"),
                      rs.getString("objectName"),
                      tableSourceTypeFrom(rs.getString("objectType")),
                      emptyList())
            , rs -> 
                    new Column(
                      rs.getString("columnName"),
                      rs.getString("columnType"),
                      rs.getInt("columnMaxLength"),
                      rs.getInt("columnPrecision"),
                      rs.getInt("columnScale"),
                      rs.getBoolean("columnNullable"),
                      rs.getString("primaryKeyName"))
            , (p, c) -> new TableSource(p.getCatalog(), p.getSchema(), p.getName(), p.getType(), c));
            //@formatter:on

            stm.getMoreResults();

            //@formatter:off
            List<Routine> routines = map(stm,
              rs -> new Routine(
                      rs.getString("objectCatalog"),
                      rs.getString("objectSchema"),
                      rs.getString("objectName"),
                      "P".equalsIgnoreCase(rs.getString("objectType")) ? Routine.Type.PROCEDURE
                              : Routine.Type.FUNCTION,
                      emptyList())
            , rs -> 
                    new RoutineParameter(
                      rs.getString("parameterName"),
                      rs.getString("parameterType"),
                      rs.getInt("parameterMaxLength"),
                      rs.getInt("parameterPrecision"),
                      rs.getInt("parameterScale"),
                      rs.getBoolean("parameterNullable"),
                      rs.getBoolean("parameterOutput"))
            , (p, c) -> new Routine(p.getCatalog(), p.getSchema(), p.getName(), p.getType(), c));
            //@formatter:on

            stm.getMoreResults();

            //@formatter:off
            List<Index> indices = map(stm,
              rs -> new Index(
                      new ObjectName(
                          rs.getString("objectCatalog"),
                          rs.getString("objectSchema"),
                          rs.getString("objectName")),
                      rs.getString("indexName"),
                      rs.getBoolean("indexIsUnique"),
                      emptyList())
            , rs -> 
                    new IndexColumn(
                      rs.getString("columnName"),
                      rs.getBoolean("columnDescending"))
            , (p, c) -> new Index(p.getObjectName(), p.getIndexName(), p.isUnique(), c));
            //@formatter:on

            stm.getMoreResults();

            //@formatter:off
            List<ForeignKey> foreignKeys = map(stm,
              rs -> new ForeignKey(
                      new ObjectName(
                          rs.getString("objectCatalog"),
                          rs.getString("objectSchema"),
                          rs.getString("objectName")),
                      emptyList())
            , rs -> 
                    new ForeignKeyColumn(
                      new ObjectName(
                          rs.getString("constrainedObjectCatalog"),
                          rs.getString("constrainedObjectSchema"),
                          rs.getString("constrainedObjectName")
                      ),
                      rs.getString("constrainedColumn"),
                      new ObjectName(
                          rs.getString("referencedObjectCatalog"),
                          rs.getString("referencedObjectSchema"),
                          rs.getString("referencedObjectName")
                      ),
                      rs.getString("referencedColumn"))
            , (p, c) -> new ForeignKey(p.getObjectName(), c));
            //@formatter:on
            // CSON

            stm.getMoreResults();

            //@formatter:off
            List<Constraint> constraints = map(stm,
              rs -> new Constraint(
                      new ObjectName(
                          rs.getString("objectCatalog"),
                          rs.getString("objectSchema"),
                          rs.getString("objectName")),
                      rs.getString("name"),
                      Constraint.Type.valueOf(rs.getString("type")),
                      rs.getString("columnName"),
                      rs.getString("definition"))
            );
            //@formatter:on
            // CSON

            LOGGER.info("Fetched metadata for " + database + " in: " + DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - time));
            return new Catalog(database, tableSources, routines, indices, foreignKeys, constraints);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beforeExecuteQuery(Connection connection, IConnectionState connectionState) throws SQLException
    {
        try (Statement stm = connection.createStatement())
        {
            stm.execute("SET SHOWPLAN_XML " + (connectionState.isEstimateQueryPlan() ? "ON"
                    : "OFF"));
            stm.execute("SET STATISTICS XML " + (!connectionState.isEstimateQueryPlan()
                    && connectionState.isIncludeQueryPlan() ? "ON"
                            : "OFF"));
        }
    }

    @Override
    public boolean processResultSet(IQueryFile queryFile, IConnectionState connectionState, ResultSet rs) throws SQLException
    {
        if ((connectionState.isIncludeQueryPlan()
                || connectionState.isEstimateQueryPlan())
                && rs.getMetaData()
                        .getColumnCount() == 1
                && CI.contains(rs.getMetaData()
                        .getColumnLabel(1), "Showplan")
                && SqlServerQueryPlanActionFactory.isSqlServerQueryPlan(rs.getString(1)))
        {
            String xml = rs.getString(1);
            SqlServerQueryPlanActionFactory.parseAndShowQueryPlan(queryFile, queryPlanOutputExtension, xml, false);
        }

        // Keep processing and show the plan, might have setting to hide the xml
        return true;
    }

    @Override
    public boolean supportsIncludeQueryPlanAction()
    {
        return true;
    }

    @Override
    public boolean supportsShowEstimatedQueryPlanAction()
    {
        return true;
    }

    private TableSource.Type tableSourceTypeFrom(String type)
    {
        // ('V', 'U', 'SN', 'TF', 'IF')
        if ("V".equalsIgnoreCase(type))
        {
            return Type.VIEW;
        }
        else if ("U".equalsIgnoreCase(type))
        {
            return Type.TABLE;
        }
        else if ("SN".equalsIgnoreCase(type))
        {
            return Type.SYNONYM;
        }
        else if ("TF".equalsIgnoreCase(type)
                || "IF".equalsIgnoreCase(type))
        {
            return Type.TABLEFUNCTION;
        }
        return Type.UNKNOWN;
    }

    private <T> List<T> map(Statement stm, ResultSetMapper<T> mapper) throws SQLException
    {
        List<T> result = new ArrayList<>();
        try (ResultSet rs = stm.getResultSet())
        {
            while (rs.next())
            {
                result.add(mapper.map(rs));
            }
        }
        return result;
    }

    private <TParent, TChild> List<TParent> map(Statement stm, ResultSetMapper<TParent> parentMapper, ResultSetMapper<TChild> childMapper, BiFunction<TParent, List<TChild>, TParent> combiner)
            throws SQLException
    {
        List<TParent> result = new ArrayList<>();

        TParent currentParent = null;
        List<TChild> currentChildren = null;

        try (ResultSet rs = stm.getResultSet())
        {
            while (rs.next())
            {
                TParent parent = parentMapper.map(rs);
                TChild child = childMapper.map(rs);
                if (currentParent == null
                        || !currentParent.equals(parent))
                {
                    if (currentChildren != null)
                    {
                        result.add(combiner.apply(currentParent, currentChildren));
                    }
                    currentChildren = new ArrayList<>();
                }
                currentChildren.add(child);
                currentParent = parent;
            }

            if (!isEmpty(currentChildren))
            {
                result.add(combiner.apply(currentParent, currentChildren));
            }
        }

        return result;
    }

    interface ResultSetMapper<T>
    {
        T map(ResultSet rs) throws SQLException;
    }
}
