package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.queryeer.api.component.QueryeerTree.RegularNode;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.jdbc.Icons;
import se.kuseman.payloadbuilder.catalog.jdbc.JdbcConnection;
import se.kuseman.payloadbuilder.catalog.jdbc.SqlConnectionSupplier;

/** Tree node supplier for Microsoft SQL Server. Adds some new nodes and context actions etc. */
class SqlServerTreeNodeSupplier extends JdbcTreeNodeSupplier
{
    SqlServerTreeNodeSupplier(JdbcDialect queryeerDatabase, IEventBus eventBus, Icons icons, QueryActionsConfigurable queryActionsConfigurable, ITemplateService templateService,
            ITreeConfig treeConfig)
    {
        super(queryeerDatabase, icons, eventBus, queryActionsConfigurable, templateService, treeConfig);
    }

    @Override
    protected ResultSet getTableTriggersResultSet(Connection connection, String database, ObjectName tableName) throws SQLException
    {
        connection.setCatalog(database);
        PreparedStatement stm = connection.prepareStatement("""
                SELECT t.name
                FROM sys.triggers t
                INNER JOIN sys.objects o
                  ON o.object_id  = t.parent_id
                  AND o.name = ?
                """);

        stm.setString(1, tableName.name());
        if (stm.execute())
        {
            return stm.getResultSet();
        }

        return null;
    }

    @Override
    protected RegularNode buildFunctionsNode(JdbcConnection jdbcConnection, String database, SqlConnectionSupplier connectionSupplier)
    {
        // SQL Server Driver returns the same for functions and procedures so we only return procedures for now
        // Should be switched to native queries instead
        return null;
    }

    @Override
    protected String getTriggerName(ResultSet rs) throws SQLException
    {
        return rs.getString("name");
    }

    @Override
    protected ObjectName getProcedureName(ResultSet rs) throws SQLException
    {
        ObjectName objectName = super.getProcedureName(rs);

        String name = objectName.name();
        int index = name.lastIndexOf(';');
        if (index > 0)
        {
            name = name.substring(0, index);
        }

        // Show schema in tree
        String title = objectName.schema() + "." + name;
        return new ObjectName(objectName.catalog(), objectName.schema(), name, title);
    }

    @Override
    protected ObjectName getTableName(ResultSet rs) throws SQLException
    {
        // Show schema in tree
        ObjectName objectName = super.getTableName(rs);
        String title = objectName.schema() + "." + objectName.name();
        return new ObjectName(objectName.catalog(), objectName.schema(), objectName.name(), title);
    }
}
