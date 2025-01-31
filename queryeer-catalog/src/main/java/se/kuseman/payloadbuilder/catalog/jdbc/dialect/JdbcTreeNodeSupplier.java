package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.apache.commons.lang3.NotImplementedException;

import com.queryeer.api.component.QueryeerTree;
import com.queryeer.api.component.QueryeerTree.RegularNode;
import com.queryeer.api.event.ExecuteQueryEvent;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.jdbc.ExecuteQueryContext;
import se.kuseman.payloadbuilder.catalog.jdbc.Icons;
import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnection;
import se.kuseman.payloadbuilder.catalog.jdbc.SqlConnectionSupplier;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.BaseDatabase.ResultSetConsumer;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.ActionTarget;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.ActionType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.QueryActionsConfigurable.QueryActionResult;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectType;

/**
 * Generic JDBC tree node supplier using plain JDBC. This class acts as base for all dialects and supplies basic nodes etc.
 */
class JdbcTreeNodeSupplier implements TreeNodeSupplier
{
    protected final JdbcDatabase jdbcDatabase;
    protected final IEventBus eventBus;
    protected final QueryActionsConfigurable queryActionsConfigurable;
    protected final Icons icons;
    protected final ITemplateService templateService;

    JdbcTreeNodeSupplier(JdbcDatabase jdbcDatabase, Icons icons, IEventBus eventBus, QueryActionsConfigurable queryActionsConfigurable, ITemplateService templateService)
    {
        this.icons = icons;
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.jdbcDatabase = requireNonNull(jdbcDatabase);
        this.queryActionsConfigurable = requireNonNull(queryActionsConfigurable, "queryActionsConfigurable");
        this.templateService = requireNonNull(templateService, "templateService");
    }

    @Override
    public List<RegularNode> getDatabaseMetaDataNodes(JdbcConnection jdbcConnection, String database, SqlConnectionSupplier sqlConnectionSupplier)
    {
        List<RegularNode> result = new ArrayList<>();

        // Tables
        // Views
        // Synonyms
        // Procedures
        // Functions
        try (Connection connection = sqlConnectionSupplier.get(jdbcConnection))
        {
            result.addAll(asList(
            //@formatter:off
                    buildTablesNode(jdbcConnection, database, sqlConnectionSupplier),
                    buildViewsNode(jdbcConnection, database, sqlConnectionSupplier),
                    buildSynonymsNode(jdbcConnection, database, sqlConnectionSupplier),
                    buildProceduresNode(jdbcConnection, database, sqlConnectionSupplier),
                    buildFunctionsNode(jdbcConnection, database, sqlConnectionSupplier)
                    //@formatter:on
            ).stream()
                    .filter(Objects::nonNull)
                    .collect(toList()));

            result.addAll(buildDatabaseMetaDataNodesExtension(jdbcConnection, database, sqlConnectionSupplier));
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
        return result;
    }

    /** Build extra nodes below database entry in tree */
    protected List<RegularNode> buildDatabaseMetaDataNodesExtension(JdbcConnection jdbcConnection, String database, SqlConnectionSupplier sqlConnectionSupplier)
    {
        return emptyList();
    }

    private void consumeResultSet(ResultSet rsIn, ResultSetConsumer consumer)
    {
        if (rsIn == null)
        {
            return;
        }

        try (ResultSet rs = rsIn)
        {
            while (rs.next())
            {
                consumer.accept(rs);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    private Set<String> getPrimaryKeys(Connection connection, String database, ObjectName tableName) throws SQLException
    {
        Set<String> primaryKeys = new HashSet<>();
        consumeResultSet(connection.getMetaData()
                .getPrimaryKeys(tableName.catalog, tableName.schema, tableName.name), rs -> primaryKeys.add(rs.getString("COLUMN_NAME")));
        return primaryKeys;
    }

    private Set<String> getForeignKeys(Connection connection, String database, ObjectName tableName) throws SQLException
    {
        Set<String> foreignKeys = new HashSet<>();
        consumeResultSet(connection.getMetaData()
                .getImportedKeys(tableName.catalog, tableName.schema, tableName.name), rs -> foreignKeys.add(rs.getString("FKCOLUMN_NAME")));
        return foreignKeys;
    }

    protected ResultSet getTablesResultSet(Connection connection, String database) throws SQLException
    {
        boolean usesSchema = jdbcDatabase.usesSchemaAsDatabase();
        return connection.getMetaData()
                .getTables(usesSchema ? null
                        : database,
                        usesSchema ? database
                                : null,
                        null, new String[] { "TABLE" });
    }

    protected ResultSet getViewsResultSet(Connection connection, String database) throws SQLException
    {
        boolean usesSchema = jdbcDatabase.usesSchemaAsDatabase();
        return connection.getMetaData()
                .getTables(usesSchema ? null
                        : database,
                        usesSchema ? database
                                : null,
                        null, new String[] { "VIEW" });
    }

    protected ResultSet getSynonymsResultSet(Connection connection, String database) throws SQLException
    {
        boolean usesSchema = jdbcDatabase.usesSchemaAsDatabase();
        return connection.getMetaData()
                .getTables(usesSchema ? null
                        : database,
                        usesSchema ? database
                                : null,
                        null, new String[] { "SYNONYM" });
    }

    protected ResultSet getProceduresResultSet(Connection connection, String database) throws SQLException
    {
        boolean usesSchema = jdbcDatabase.usesSchemaAsDatabase();
        return connection.getMetaData()
                .getProcedures(usesSchema ? null
                        : database,
                        usesSchema ? database
                                : null,
                        null);
    }

    protected ResultSet getFunctionsResultSet(Connection connection, String database) throws SQLException
    {
        boolean usesSchema = jdbcDatabase.usesSchemaAsDatabase();
        return connection.getMetaData()
                .getFunctions(usesSchema ? null
                        : database,
                        usesSchema ? database
                                : null,
                        null);
    }

    protected ResultSet getTableColumnsResultSet(Connection connection, String database, ObjectName tableName) throws SQLException
    {
        return connection.getMetaData()
                .getColumns(tableName.catalog, tableName.schema, tableName.name, null);
    }

    protected ResultSet getViewColumnsResultSet(Connection connection, String database, ObjectName viewName) throws SQLException
    {
        return connection.getMetaData()
                .getColumns(viewName.catalog, viewName.schema, viewName.name, null);
    }

    protected ResultSet getSynonymColumnsResultSet(Connection connection, String database, ObjectName synonymName) throws SQLException
    {
        return connection.getMetaData()
                .getColumns(synonymName.catalog, synonymName.schema, synonymName.name, null);
    }

    protected ResultSet getProcedureParametersResultSet(Connection connection, String database, ObjectName procedureName) throws SQLException
    {
        return connection.getMetaData()
                .getProcedureColumns(procedureName.catalog, procedureName.schema, procedureName.name, null);
    }

    protected ResultSet getFunctionParametersResultSet(Connection connection, String database, ObjectName functionName) throws SQLException
    {
        return connection.getMetaData()
                .getFunctionColumns(functionName.catalog, functionName.schema, functionName.name, null);
    }

    protected ResultSet getTableIndicesResultSet(Connection connection, String database, ObjectName tableName) throws SQLException
    {
        return connection.getMetaData()
                .getIndexInfo(tableName.catalog, tableName.schema, tableName.name, false, true);
    }

    protected ResultSet getTableForeignKeysResultSet(Connection connection, String database, ObjectName tableName) throws SQLException
    {
        return connection.getMetaData()
                .getImportedKeys(tableName.catalog, tableName.schema, tableName.name);
    }

    // Not implemented in base node suppliers
    protected ResultSet getTableTriggersResultSet(Connection connection, String database, ObjectName tableName) throws SQLException
    {
        return null;
    }

    /** Builds the 'Tables' node */
    protected RegularNode buildTablesNode(JdbcConnection jdbcConnection, String database, SqlConnectionSupplier sqlConnectionSupplier)
    {
        return new QueryeerTree.TreeNode("Tables", icons.folder_o, () -> buildTablesChildNodes(jdbcConnection, sqlConnectionSupplier, database));
    }

    /** Builds the 'Views' node */
    protected RegularNode buildViewsNode(JdbcConnection jdbcConnection, String database, SqlConnectionSupplier sqlConnectionSupplier)
    {
        return new QueryeerTree.TreeNode("Views", icons.folder_o, () -> buildViewsChildNodes(jdbcConnection, sqlConnectionSupplier, database));
    }

    /** Builds the 'Synonyms' node */
    protected RegularNode buildSynonymsNode(JdbcConnection jdbcConnection, String database, SqlConnectionSupplier sqlConnectionSupplier)
    {
        return new QueryeerTree.TreeNode("Synonyms", icons.folder_o, () -> buildSynonymsChildNodes(jdbcConnection, sqlConnectionSupplier, database));
    }

    /** Builds the 'Procedures' node */
    protected RegularNode buildProceduresNode(JdbcConnection jdbcConnection, String database, SqlConnectionSupplier sqlConnectionSupplier)
    {
        return new QueryeerTree.TreeNode("Procedures", icons.folder_o, () -> buildProceduresChildNodes(jdbcConnection, sqlConnectionSupplier, database));
    }

    /** Builds the 'Functions' node */
    protected RegularNode buildFunctionsNode(JdbcConnection jdbcConnection, String database, SqlConnectionSupplier sqlConnectionSupplier)
    {
        return new QueryeerTree.TreeNode("Functions", icons.folder_o, () -> buildFunctionsChildNodes(jdbcConnection, sqlConnectionSupplier, database));
    }

    /** Builds the nodes below 'Tables' node */
    protected List<RegularNode> buildTablesChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getTablesResultSet(con, database), rs -> result.add(buildTableNode(jdbcConnection, sqlConnectionSupplier, rs, database)));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds the nodes below 'Views' node */
    protected List<RegularNode> buildViewsChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getViewsResultSet(con, database), rs -> result.add(buildViewNode(jdbcConnection, sqlConnectionSupplier, rs, database)));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds the nodes below 'Synonyms' node */
    protected List<RegularNode> buildSynonymsChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getSynonymsResultSet(con, database), rs -> result.add(buildSynonymNode(jdbcConnection, sqlConnectionSupplier, rs, database)));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds the nodes below 'Procedures' node */
    protected List<RegularNode> buildProceduresChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getProceduresResultSet(con, database), rs -> result.add(buildProcedureNode(jdbcConnection, sqlConnectionSupplier, rs, database)));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds the nodes below 'Functions' node */
    protected List<RegularNode> buildFunctionsChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getFunctionsResultSet(con, database), rs -> result.add(buildFunctionNode(jdbcConnection, sqlConnectionSupplier, rs, database)));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private List<Action> getQueryActions(JdbcConnection jdbcConnection, String database, ActionType actionType, ObjectType objectType, Map<String, Object> model)
    {
        List<Action> result = new ArrayList<>();
        List<QueryActionResult> queryActions = queryActionsConfigurable.getQueryActions(jdbcConnection.getJdbcURL(), database, ActionTarget.NAVIGATION_TREE, actionType, Set.of(objectType));
        for (QueryActionsConfigurable.QueryActionResult queryAction : queryActions)
        {
            Action action = buildAction(jdbcConnection, database, queryAction, model);
            if (action != null)
            {
                result.add(action);
            }
        }
        return result;
    }

    private Action buildAction(JdbcConnection jdbcConnection, String database, QueryActionsConfigurable.QueryActionResult queryAction, Map<String, Object> model)
    {
        Action action = null;
        if (!queryAction.hasSubItems())
        {
            String query = queryAction.query();
            action = new AbstractAction(queryAction.title())
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    eventBus.publish(
                            new ExecuteQueryEvent(queryAction.output(), new ExecuteQueryContext(jdbcConnection, jdbcDatabase, database, templateService.process(queryAction.title(), query, model))));
                }
            };
        }
        else
        {
            List<Action> subActions = new ArrayList<>(queryAction.subItems()
                    .size());
            for (QueryActionsConfigurable.QueryActionResult subQueryAction : queryAction.subItems())
            {
                Action subAction = buildAction(jdbcConnection, database, subQueryAction, model);
                if (subAction != null)
                {
                    subActions.add(subAction);
                }
            }
            if (!subActions.isEmpty())
            {
                action = new AbstractAction(queryAction.title())
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                    }
                };

                action.putValue(com.queryeer.api.action.Constants.SUB_ACTIONS, subActions);
            }
        }
        return action;
    }

    /** Builds a 'Table' node of the result from {@link #getTablesResultSet} */
    protected RegularNode buildTableNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        ObjectName tableName = getTableName(rs);

        //@formatter:off
        Map<String, Object> model = Map.of(
                "catalog", Objects.toString(tableName.catalog, ""),
                "schema", Objects.toString(tableName.schema, ""),
                "name", Objects.toString(tableName.name, ""));
        //@formatter:on
        List<Action> contextMenuActions = getTableContextMenuActions(jdbcConnection, sqlConnectionSupplier, rs, database);
        List<Action> linkActions = getTableLinkActions(jdbcConnection, sqlConnectionSupplier, rs, database);

        return new QueryeerTree.TreeNode(tableName.title, icons.tableIcon, () -> buildTableChildNodes(jdbcConnection, sqlConnectionSupplier, database, tableName), () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.CONTEXT_MENU, ObjectType.TABLE, model);
            actions.addAll(contextMenuActions);
            return actions;
        }, () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.LINK, ObjectType.TABLE, model);
            actions.addAll(linkActions);
            return actions;
        });
    }

    record ObjectName(String catalog, String schema, String name, String title)
    {
    }

    /** Return the view node name of a table */
    protected ObjectName getTableName(ResultSet rs) throws SQLException
    {
        // TABLE_CAT String => table catalog (may be null)
        // TABLE_SCHEM String => table schema (may be null)
        // TABLE_NAME String => table name

        String cat = rs.getString("TABLE_CAT");
        String schem = rs.getString("TABLE_SCHEM");
        String name = rs.getString("TABLE_NAME");

        String title = (jdbcDatabase.usesSchemaAsDatabase() ? (!isBlank(schem) ? (schem + ".")
                : "")
                : (!isBlank(cat) ? (cat + ".")
                        : ""))
                + name;

        return new ObjectName(cat, schem, name, title);
    }

    protected List<Action> getTableLinkActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    protected List<Action> getTableContextMenuActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    // protected List<Action> getTableLinkActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    // {
    // String tableName = getTableName(rs);
    // return asList(new AbstractAction("Top 500")
    // {
    // @Override
    // public void actionPerformed(ActionEvent e)
    // {
    // eventBus.publish(new ExecuteQueryEvent(ITableOutputComponent.class, new ExecuteQueryContext(jdbcConnection, jdbcDatabase, database, jdbcDatabase.getTopXQuery(500, tableName))));
    // }
    // });
    // }

    // protected List<Action> getTableContextMenuActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database)
    // {
    // return emptyList();
    // }

    /** Builds nodes below a 'Table' node */
    protected List<RegularNode> buildTableChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName tableName)
    {
        // Columns
        // Indices
        // Foreign Keys

        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            result.add(buildTableColumnsNode(jdbcConnection, sqlConnectionSupplier, database, tableName));
            result.add(buildTableIndicesNode(jdbcConnection, sqlConnectionSupplier, database, tableName));
            result.add(buildTableForeignKeysNode(jdbcConnection, sqlConnectionSupplier, database, tableName));
            result.add(buildTableTriggersNode(jdbcConnection, sqlConnectionSupplier, database, tableName));
            result.addAll(buildTableChildNodesExtended(jdbcConnection, sqlConnectionSupplier, con, database, tableName));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Extension method to easier add nodes below a table. NOTE! Connection should not be closed! */
    protected List<RegularNode> buildTableChildNodesExtended(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, Connection connection, String database, ObjectName tableName)
            throws SQLException
    {
        return emptyList();
    }

    /** Builds the 'Columns' node for a table */
    protected RegularNode buildTableColumnsNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName tableName)
    {
        return new QueryeerTree.TreeNode("Columns", icons.folder_o, () -> buildTableColumnsChildNodes(jdbcConnection, sqlConnectionSupplier, database, tableName));
    }

    /** Builds the 'Indices' node for a table */
    protected RegularNode buildTableIndicesNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName tableName)
    {
        return new QueryeerTree.TreeNode("Indices", icons.folder_o, () -> buildTableIndicesChildNodes(jdbcConnection, sqlConnectionSupplier, database, tableName));
    }

    /** Builds the 'Foreign Keys' node for a table */
    protected RegularNode buildTableForeignKeysNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName tableName)
    {
        return new QueryeerTree.TreeNode("Foreign Keys", icons.folder_o, () -> buildTableForeignKeysChildNodes(jdbcConnection, sqlConnectionSupplier, database, tableName));
    }

    /** Builds the 'Triggers' node for a table */
    protected RegularNode buildTableTriggersNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName tableName)
    {
        return new QueryeerTree.TreeNode("Triggers", icons.folder_o, () -> buildTableTriggersChildNodes(jdbcConnection, sqlConnectionSupplier, database, tableName));
    }

    /** Builds the columns nodes below 'Columns' for a table */
    protected List<RegularNode> buildTableColumnsChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName tableName)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            Set<String> primaryKeys = getPrimaryKeys(con, database, tableName);
            Set<String> foreignKeys = getForeignKeys(con, database, tableName);
            consumeResultSet(getTableColumnsResultSet(con, database, tableName), rs -> result.add(buildColumnNode(rs, primaryKeys, foreignKeys)));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds the indices nodes below 'Indices' for a table */
    protected List<RegularNode> buildTableIndicesChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName tableName)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getTableIndicesResultSet(con, database, tableName), rs ->
            {
                RegularNode node = buildIndexNode(rs);
                if (node != null)
                {
                    result.add(node);
                }
            });
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds the foreign keys nodes below 'Foreign Keys' for a table */
    protected List<RegularNode> buildTableForeignKeysChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName tableName)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getTableForeignKeysResultSet(con, database, tableName), rs -> result.add(buildForeignKeyNode(rs)));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds the triggers nodes below 'Triggers' for a table */
    protected List<RegularNode> buildTableTriggersChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName tableName)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getTableTriggersResultSet(con, database, tableName), rs -> result.add(buildTriggerNode(jdbcConnection, sqlConnectionSupplier, rs, database)));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds a 'View' node from the result of {@link #getViewsResultSet} */
    protected RegularNode buildViewNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        ObjectName viewName = getTableName(rs);

        //@formatter:off
        Map<String, Object> model =  Map.of(
                "catalog", Objects.toString(viewName.catalog, ""),
                "schema", Objects.toString(viewName.schema, ""),
                "name", Objects.toString(viewName.name, ""));
        //@formatter:on

        List<Action> contextMenuActions = getTableContextMenuActions(jdbcConnection, sqlConnectionSupplier, rs, database);
        List<Action> linkActions = getTableLinkActions(jdbcConnection, sqlConnectionSupplier, rs, database);

        return new QueryeerTree.TreeNode(viewName.title, icons.viewIcon, () -> buildViewChildNodes(jdbcConnection, sqlConnectionSupplier, database, viewName), () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.CONTEXT_MENU, ObjectType.VIEW, model);
            actions.addAll(contextMenuActions);
            return actions;
        }, () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.LINK, ObjectType.VIEW, model);
            actions.addAll(linkActions);
            return actions;
        });
    }

    protected List<Action> getViewLinkActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    protected List<Action> getViewContextMenuActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    /** Builds nodes below a 'View' node */
    protected List<RegularNode> buildViewChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName viewName)
    {
        // Columns
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            result.add(buildViewColumnsNode(jdbcConnection, sqlConnectionSupplier, database, viewName));
            result.addAll(buildViewChildNodesExtended(jdbcConnection, sqlConnectionSupplier, con, database, viewName));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Extension method to easier add nodes below a view. NOTE! Connection should not be closed! */
    protected List<RegularNode> buildViewChildNodesExtended(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, Connection connection, String database, ObjectName viewName)
    {
        return emptyList();
    }

    /** Builds the 'Columns' node for a view */
    protected RegularNode buildViewColumnsNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName viewName)
    {
        return new QueryeerTree.TreeNode("Columns", icons.folder_o, () -> buildViewColumnsChildNodes(jdbcConnection, sqlConnectionSupplier, database, viewName));
    }

    /** Builds the columns nodes below 'Columns' for a view */
    protected List<RegularNode> buildViewColumnsChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName viewName)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getViewColumnsResultSet(con, database, viewName), rs -> result.add(buildColumnNode(rs, emptySet(), emptySet())));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds a 'Synonym' node of the result from {@link #getSynonymsResultSet} */
    protected RegularNode buildSynonymNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        ObjectName synonymName = getTableName(rs);

        //@formatter:off
        Map<String, Object> model =  Map.of(
                "catalog", Objects.toString(synonymName.catalog, ""),
                "schema", Objects.toString(synonymName.schema, ""),
                "name", Objects.toString(synonymName.name, ""));
        //@formatter:on

        List<Action> contextMenuActions = getSynonymContextMenuActions(jdbcConnection, sqlConnectionSupplier, rs, database);
        List<Action> linkActions = getSynonymLinkActions(jdbcConnection, sqlConnectionSupplier, rs, database);

        return new QueryeerTree.TreeNode(synonymName.title, icons.viewIcon, () -> buildSynonymChildNodes(jdbcConnection, sqlConnectionSupplier, database, synonymName), () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.CONTEXT_MENU, ObjectType.SYNONYM, model);
            actions.addAll(contextMenuActions);
            return actions;
        }, () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.LINK, ObjectType.SYNONYM, model);
            actions.addAll(linkActions);
            return actions;
        });
    }

    protected List<Action> getSynonymLinkActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    protected List<Action> getSynonymContextMenuActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    /** Builds a 'Trigger' node from the result of {@link #getTableTriggersResultSet} */
    protected RegularNode buildTriggerNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        String triggerName = getTriggerName(rs);

        //@formatter:off
        Map<String, Object> model =  Map.of(
              "name", Objects.toString(triggerName, ""));
        //@formatter:on

        List<Action> contextMenuActions = getTriggerContextMenuActions(jdbcConnection, sqlConnectionSupplier, rs, database);
        List<Action> linkActions = getTriggerLinkActions(jdbcConnection, sqlConnectionSupplier, rs, database);

        return new QueryeerTree.TreeNode(triggerName, icons.boltIcon, () -> buildTriggerChildNodes(jdbcConnection, sqlConnectionSupplier, database, triggerName), () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.CONTEXT_MENU, ObjectType.TRIGGER, model);
            actions.addAll(contextMenuActions);
            return actions;
        }, () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.LINK, ObjectType.TRIGGER, model);
            actions.addAll(linkActions);
            return actions;
        });
    }

    protected String getTriggerName(ResultSet rs) throws SQLException
    {
        throw new NotImplementedException();
    }

    protected List<Action> getTriggerLinkActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    protected List<Action> getTriggerContextMenuActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    // protected List<Action> getSynonymLinkActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    // {
    // String getSynonymName = getSynonymName(rs);
    // return asList(new AbstractAction("Top 500")
    // {
    // @Override
    // public void actionPerformed(ActionEvent e)
    // {
    // eventBus.publish(new ExecuteQueryEvent(ITableOutputComponent.class, new ExecuteQueryContext(jdbcConnection, jdbcDatabase, database, jdbcDatabase.getTopXQuery(500, getSynonymName))));
    // }
    // });
    // }
    //
    // protected List<Action> getSynonymContextMenuActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    // {
    // return emptyList();
    // }

    protected List<RegularNode> buildTriggerChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, String trigger)
    {
        return emptyList();
    }

    /** Builds a 'Procedure' node of the result from {@link #getProceduresResultSet} */
    protected RegularNode buildProcedureNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        ObjectName procedureName = getProcedureName(rs);

        //@formatter:off
        Map<String, Object> model =  Map.of(
                "catalog", Objects.toString(procedureName.catalog, ""),
                "schema", Objects.toString(procedureName.schema, ""),
                "name", Objects.toString(procedureName.name, ""));
        //@formatter:on

        List<Action> contextMenuActions = getProcedureContextMenuActions(jdbcConnection, sqlConnectionSupplier, rs, database);
        List<Action> linkActions = getProcedureLinkActions(jdbcConnection, sqlConnectionSupplier, rs, database);

        return new QueryeerTree.TreeNode(procedureName.title, icons.wpformsIcon, () -> buildProcedureChildNodes(jdbcConnection, sqlConnectionSupplier, database, procedureName), () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.CONTEXT_MENU, ObjectType.PROCEDURE, model);
            actions.addAll(contextMenuActions);
            return actions;
        }, () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.LINK, ObjectType.PROCEDURE, model);
            actions.addAll(linkActions);
            return actions;
        });
    }

    /** Return the object name of procedure */
    protected ObjectName getProcedureName(ResultSet rs) throws SQLException
    {
        // PROCEDURE_CAT String => procedure catalog (may be null)
        // PROCEDURE_SCHEM String => procedure schema (may be null)
        // PROCEDURE_NAME String => procedure name

        String cat = rs.getString("PROCEDURE_CAT");
        String schem = rs.getString("PROCEDURE_SCHEM");
        String name = rs.getString("PROCEDURE_NAME");

        String title = (jdbcDatabase.usesSchemaAsDatabase() ? (!isBlank(schem) ? (schem + ".")
                : "")
                : (!isBlank(cat) ? (cat + ".")
                        : ""))
                + name;

        return new ObjectName(cat, schem, name, title);
    }

    protected List<Action> getProcedureContextMenuActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    protected List<Action> getProcedureLinkActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    /** Builds a 'Function' node of the result from {@link #getFunctionsResultSet} */
    protected RegularNode buildFunctionNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        ObjectName functionName = getFunctionName(rs);

        //@formatter:off
        Map<String, Object> model =  Map.of(
                "catalog", Objects.toString(functionName.catalog, ""),
                "schema", Objects.toString(functionName.schema, ""),
                "name", Objects.toString(functionName.name, ""));
        //@formatter:on

        List<Action> contextMenuActions = getFunctionContextMenuActions(jdbcConnection, sqlConnectionSupplier, rs, database);
        List<Action> linkActions = getFunctionLinkActions(jdbcConnection, sqlConnectionSupplier, rs, database);

        return new QueryeerTree.TreeNode(functionName.title, icons.fileTextIcon, () -> buildFunctionChildNodes(jdbcConnection, sqlConnectionSupplier, database, functionName), () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.CONTEXT_MENU, ObjectType.FUNCTION, model);
            actions.addAll(contextMenuActions);
            return actions;
        }, () ->
        {
            List<Action> actions = getQueryActions(jdbcConnection, database, ActionType.LINK, ObjectType.FUNCTION, model);
            actions.addAll(linkActions);
            return actions;
        });
    }

    /** Return the object name of function */
    protected ObjectName getFunctionName(ResultSet rs) throws SQLException
    {
        // FUNCTION_CAT String => function catalog (may be null)
        // FUNCTION_SCHEM String => function schema (may be null)
        // FUNCTION_NAME String => function name

        String cat = rs.getString("FUNCTION_CAT");
        String schem = rs.getString("FUNCTION_SCHEM");
        String name = rs.getString("FUNCTION_NAME");

        String title = (jdbcDatabase.usesSchemaAsDatabase() ? (!isBlank(schem) ? (schem + ".")
                : "")
                : (!isBlank(cat) ? (cat + ".")
                        : ""))
                + name;

        return new ObjectName(cat, schem, name, title);
    }

    protected List<Action> getFunctionContextMenuActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    protected List<Action> getFunctionLinkActions(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, ResultSet rs, String database) throws SQLException
    {
        return emptyList();
    }

    /** Builds nodes below a 'Synonym' node */
    protected List<RegularNode> buildSynonymChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName synonymName)
    {
        // Columns
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            result.add(buildSynonymColumnsNode(jdbcConnection, sqlConnectionSupplier, database, synonymName));
            result.addAll(buildSynonymChildNodesExtended(jdbcConnection, sqlConnectionSupplier, con, database, synonymName));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds nodes below a 'Procedure' node */
    protected List<RegularNode> buildProcedureChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName procedureName)
    {
        // Parameters
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            result.add(buildProcedureParametersNode(jdbcConnection, sqlConnectionSupplier, database, procedureName));
            result.addAll(buildProcedureChildNodesExtended(jdbcConnection, sqlConnectionSupplier, con, database, procedureName));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds nodes below a 'Function' node */
    protected List<RegularNode> buildFunctionChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName functionName)
    {
        // Parameters
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            result.add(buildFunctionParametersNode(jdbcConnection, sqlConnectionSupplier, database, functionName));
            result.addAll(buildFunctionChildNodesExtended(jdbcConnection, sqlConnectionSupplier, con, database, functionName));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Extension method to easier add nodes below a function. NOTE! Connection should not be closed! */
    protected List<RegularNode> buildFunctionChildNodesExtended(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, Connection connection, String database,
            ObjectName functionName)
    {
        return emptyList();
    }

    /** Extension method to easier add nodes below a procedure. NOTE! Connection should not be closed! */
    protected List<RegularNode> buildProcedureChildNodesExtended(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, Connection connection, String database,
            ObjectName procedureName)
    {
        return emptyList();
    }

    /** Extension method to easier add nodes below a synonym. NOTE! Connection should not be closed! */
    protected List<RegularNode> buildSynonymChildNodesExtended(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, Connection connection, String database,
            ObjectName synonymName)
    {
        return emptyList();
    }

    /** Builds the 'Columns' node for a synonym */
    protected RegularNode buildSynonymColumnsNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName viewName)
    {
        return new QueryeerTree.TreeNode("Columns", icons.folder_o, () -> buildSynonymColumnsChildNodes(jdbcConnection, sqlConnectionSupplier, database, viewName));
    }

    /** Builds the 'Parameters' node for a procedure */
    protected RegularNode buildProcedureParametersNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName procedureName)
    {
        return new QueryeerTree.TreeNode("Parameters", icons.folder_o, () -> buildProcedureParametersChildNodes(jdbcConnection, sqlConnectionSupplier, database, procedureName));
    }

    /** Builds the 'Parameters' node for a function */
    protected RegularNode buildFunctionParametersNode(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName functioName)
    {
        return new QueryeerTree.TreeNode("Parameters", icons.folder_o, () -> buildFunctionParametersChildNodes(jdbcConnection, sqlConnectionSupplier, database, functioName));
    }

    /** Builds the columns nodes below 'Columns' for a synonym */
    protected List<RegularNode> buildSynonymColumnsChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName synonymName)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getSynonymColumnsResultSet(con, database, synonymName), rs -> result.add(buildColumnNode(rs, emptySet(), emptySet())));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds the parameters nodes below 'Parameters' for a procedure */
    protected List<RegularNode> buildProcedureParametersChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName procedureName)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getProcedureParametersResultSet(con, database, procedureName), rs -> result.add(buildProcedureParameterNode(rs)));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds the parameters nodes below 'Parameters' for a function */
    protected List<RegularNode> buildFunctionParametersChildNodes(JdbcConnection jdbcConnection, SqlConnectionSupplier sqlConnectionSupplier, String database, ObjectName functionName)
    {
        try (Connection con = sqlConnectionSupplier.get(jdbcConnection))
        {
            List<RegularNode> result = new ArrayList<>();
            consumeResultSet(getFunctionParametersResultSet(con, database, functionName), rs -> result.add(buildProcedureParameterNode(rs)));
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Builds a procedure parameter node of the result from {@link #getProcedureParametersResultSet}, {@link #getFunctionParametersResultSet} */
    protected QueryeerTree.TreeNode buildProcedureParameterNode(ResultSet rs) throws SQLException
    {
        // CSOFF
        String columnName = rs.getString("COLUMN_NAME");
        String columnDefault = rs.getString("COLUMN_DEF");
        int columnType = rs.getInt("COLUMN_TYPE");
        String typeName = rs.getString("TYPE_NAME");
        int columnSize = rs.getInt("LENGTH");
        Object scale = rs.getObject("SCALE");
        boolean nullable = rs.getBoolean("NULLABLE");
        // CSON

        // COLUMN_TYPE Short => kind of column/parameter: ◦ procedureColumnUnknown - nobody knows
        // ◦ procedureColumnIn - IN parameter
        // ◦ procedureColumnInOut - INOUT parameter
        // ◦ procedureColumnOut - OUT parameter
        // ◦ procedureColumnReturn - procedure return value
        // ◦ procedureColumnResult - result column in ResultSet

        boolean isReturn = columnType == DatabaseMetaData.procedureColumnReturn;

        // CSOFF
        String columnTypeStr = switch (columnType)
        {
            case DatabaseMetaData.procedureColumnIn -> "Input";
            case DatabaseMetaData.procedureColumnOut -> "Output";
            case DatabaseMetaData.procedureColumnInOut -> "Input/Output";
            default -> "";
        };
        // CSON

        // @pParam (type((length, scale), input, default)
        StringBuilder sb = new StringBuilder();
        sb.append(isReturn ? "Returns "
                : columnName)
                .append(" (");

        sb.append(typeName);
        sb.append('(');
        sb.append(columnSize);

        if (scale != null)
        {
            sb.append(", ");
            sb.append(scale);
        }
        sb.append(')');

        sb.append(", ");
        sb.append(nullable ? "null"
                : "not null");

        if (!isBlank(columnTypeStr))
        {
            sb.append(", ")
                    .append(columnTypeStr);
        }

        sb.append(", ")
                .append(!isBlank(columnDefault) ? columnDefault
                        : "No Default");

        sb.append(")");

        return new QueryeerTree.TreeNode(sb.toString(), icons.atIcon);
    }

    /** Builds a column node of the result from {@link #getTableColumnsResultSet}, {@link #getViewColumnsResultSet}, {@link #getSynonymsResultSet} */
    protected QueryeerTree.TreeNode buildColumnNode(ResultSet rs, Set<String> primaryKeys, Set<String> foreignKeys) throws SQLException
    {
        // CSOFF
        String columnName = rs.getString("COLUMN_NAME");
        String typeName = rs.getString("TYPE_NAME");
        int columnSize = rs.getInt("COLUMN_SIZE");
        Object decimalDigits = rs.getObject("DECIMAL_DIGITS");
        boolean nullable = rs.getBoolean("NULLABLE");
        // CSON
        boolean pk = primaryKeys.contains(columnName);
        boolean fk = foreignKeys.contains(columnName);

        StringBuilder sb = new StringBuilder();
        sb.append(columnName)
                .append(" (");

        if (pk)
        {
            sb.append("PK, ");
        }
        if (fk)
        {
            sb.append("FK, ");
        }

        sb.append(typeName);
        sb.append('(');
        if (columnSize == Integer.MAX_VALUE)
        {
            sb.append("MAX");
        }
        else
        {
            sb.append(columnSize);
        }
        if (decimalDigits != null)
        {
            sb.append(", ");
            sb.append(decimalDigits);
        }
        sb.append(')');

        sb.append(", ");
        sb.append(nullable ? "null"
                : "not null");
        sb.append(")");

        return new QueryeerTree.TreeNode(sb.toString(), pk
                || fk ? icons.keyIcon
                        : icons.columnsIcon);
    }

    /** Builds a tree node for a table index from result of {@link #getTableIndicesResultSet} */
    protected QueryeerTree.TreeNode buildIndexNode(ResultSet rs) throws SQLException
    {
        int type = rs.getInt("TYPE");

        if (type == DatabaseMetaData.tableIndexStatistic)
        {
            return null;
        }

        // CSOFF
        String indexName = rs.getString("INDEX_NAME");
        String columnName = rs.getString("COLUMN_NAME");
        String ascOrDesc = rs.getString("ASC_OR_DESC");
        boolean nonUnique = rs.getBoolean("NON_UNIQUE");
        // CSON
        StringBuilder sb = new StringBuilder();
        sb.append(indexName)
                .append(" (");

        sb.append(columnName)
                .append(' ');

        if (type == DatabaseMetaData.tableIndexClustered)
        {
            sb.append("CLUSTERED ");
        }
        if ("A".equalsIgnoreCase(ascOrDesc))
        {
            sb.append("ASCENDING ");
        }
        else if ("D".equalsIgnoreCase(ascOrDesc))
        {
            sb.append("DESCENDING ");
        }
        if (!nonUnique)
        {
            sb.append("UNIQUE");
        }
        sb.append(")");

        return new QueryeerTree.TreeNode(sb.toString(), icons.reorderIcon);
    }

    /** Builds a foreign key node from the result of {@link #getTableForeignKeysResultSet} */
    protected QueryeerTree.TreeNode buildForeignKeyNode(ResultSet rs) throws SQLException
    {
        // CSOFF
        String fKName = rs.getString("FK_NAME");
        String fkColumnName = rs.getString("FKCOLUMN_NAME");
        String pkTableName = rs.getString("PKTABLE_NAME");
        String pkColumnName = rs.getString("PKCOLUMN_NAME");
        // CSON
        String title = "%s (%s) -> %s (%s)".formatted(fKName, fkColumnName, pkTableName, pkColumnName);
        return new QueryeerTree.TreeNode(title.toString(), icons.keyIcon);
    }

}
