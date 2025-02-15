package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import org.apache.commons.lang3.StringUtils;

import com.queryeer.api.extensions.Inject;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.Icons;

/** Provider for returning a {@link JdbcDatabase} */
@Inject
public class DatabaseProvider
{
    private final SqlServerDatabase sqlServer;
    private final OracleDatabase oracle;
    private final BaseDatabase jdbc;

    public DatabaseProvider(CatalogCrawlService crawlService, IEventBus eventBus, Icons icons, QueryActionsConfigurable queryActionsConfigurable, ITemplateService templateService,
            IQueryPlanOutputExtension queryPlanOutputExtension, ITreeConfig treeConfig)
    {
        sqlServer = new SqlServerDatabase(crawlService, eventBus, icons, queryActionsConfigurable, templateService, queryPlanOutputExtension, treeConfig);
        jdbc = new BaseDatabase(icons, crawlService, eventBus, queryActionsConfigurable, templateService, treeConfig);
        oracle = new OracleDatabase(icons, crawlService, eventBus, queryActionsConfigurable, templateService, treeConfig);
    }

    /** Return a {@link JdbcDatabase} from provided {@link SqlDialect} */
    public JdbcDatabase getDatabase(String url)
    {
        if (StringUtils.containsAnyIgnoreCase(url, "oracle"))
        {
            return oracle;
        }
        else if (StringUtils.containsAnyIgnoreCase(url, "sqlserver"))
        {
            return sqlServer;
        }
        return jdbc;
    }
}
