package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.Icons;

/** Database support for Oracle */
class OracleDatabase extends BaseDatabase implements JdbcDatabase
{
    static final String NAME = "oracle";
    private static final Logger LOGGER = LoggerFactory.getLogger(SqlServerDatabase.class);

    OracleDatabase(Icons icons, CatalogCrawlService crawlService, IEventBus eventBus, QueryActionsConfigurable queryActionsConfigurable, ITemplateService templateService)
    {
        super(icons, crawlService, eventBus, queryActionsConfigurable, templateService);
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public String getSessionKeyword()
    {
        return "AUDSID";
    }

    /** Returns true if this dialect uses JDBC schemas as database */
    @Override
    public boolean usesSchemaAsDatabase()
    {
        return true;
    }

    @Override
    public String getSessionId(Connection connection)
    {
        try (Statement stm = connection.createStatement(); ResultSet rs = stm.executeQuery("SELECT USERENV('SESSIONID') FROM DUAL"))
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

    /*
     * Query plans -----------
     * 
     * - Estimated EXPLAIN PLAN FOR <query>
     * 
     * select dbms_xplan.display_plan(format=>'ALL', type=>'xml') from dual
     * 
     * - Actual
     * 
     * 
     */
}
