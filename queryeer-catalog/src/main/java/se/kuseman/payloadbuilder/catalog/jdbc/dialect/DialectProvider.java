package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static org.apache.commons.lang3.Strings.CI;

import com.queryeer.api.extensions.Inject;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputExtension;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

import se.kuseman.payloadbuilder.catalog.jdbc.CatalogCrawlService;
import se.kuseman.payloadbuilder.catalog.jdbc.Icons;

/** Provider for returning a {@link JdbcDialect} */
@Inject
public class DialectProvider
{
    private final SqlServerDialect sqlServer;
    private final OracleDialect oracle;
    private final BaseDialect jdbc;

    public DialectProvider(CatalogCrawlService crawlService, IEventBus eventBus, Icons icons, QueryActionsConfigurable queryActionsConfigurable, ITemplateService templateService,
            IQueryPlanOutputExtension queryPlanOutputExtension, ITreeConfig treeConfig)
    {
        sqlServer = new SqlServerDialect(crawlService, eventBus, icons, queryActionsConfigurable, templateService, queryPlanOutputExtension, treeConfig);
        jdbc = new BaseDialect(icons, crawlService, eventBus, queryActionsConfigurable, templateService, treeConfig);
        oracle = new OracleDialect(icons, crawlService, eventBus, queryActionsConfigurable, templateService, treeConfig);
    }

    /** Return a {@link JdbcDialect} from provided {@link SqlDialect} */
    public JdbcDialect getDialect(String url)
    {
        if (CI.contains(url, "oracle"))
        {
            return oracle;
        }
        else if (CI.contains(url, "sqlserver"))
        {
            return sqlServer;
        }
        return jdbc;
    }
}
