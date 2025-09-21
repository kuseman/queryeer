package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import java.sql.Driver;
import java.util.Iterator;
import java.util.ServiceLoader;

import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtensionFactory;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialectProvider;

/** Factory for {@link JdbcCatalogExtension}. */
class JdbcCatalogExtensionFactory implements ICatalogExtensionFactory
{
    private final JdbcConnectionsModel connectionsModel;
    private final IQueryFileProvider queryFileProvider;
    private final Icons icons;
    private final CatalogCrawlService crawlService;
    private final JdbcDialectProvider dialectProvider;

    static
    {
        // Initialize SQL Drivers with this class loader since DrvierManger won't find them
        // because of custom class loader of queyeer plugins
        try
        {
            ServiceLoader<Driver> load = ServiceLoader.load(java.sql.Driver.class, JdbcCatalogExtensionFactory.class.getClassLoader());
            Iterator<Driver> it = load.iterator();
            while (it.hasNext())
            {
                it.next();
            }
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }

    public JdbcCatalogExtensionFactory(IQueryFileProvider queryFileProvider, JdbcConnectionsModel connectionsModel, Icons icons, CatalogCrawlService crawlService, JdbcDialectProvider dialectProvider)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.icons = requireNonNull(icons, "icons");
        this.crawlService = requireNonNull(crawlService, "crawlService");
        this.dialectProvider = requireNonNull(dialectProvider, "dialectProvider");
    }

    @Override
    public ICatalogExtension create(String catalogAlias)
    {
        return new JdbcCatalogExtension(connectionsModel, queryFileProvider, crawlService, icons, dialectProvider, catalogAlias);
    }

    @Override
    public String getTitle()
    {
        return Constants.TITLE;
    }

    @Override
    public String getDefaultAlias()
    {
        return "jdbc";
    }

    @Override
    public int order()
    {
        return 1;
    }
}
