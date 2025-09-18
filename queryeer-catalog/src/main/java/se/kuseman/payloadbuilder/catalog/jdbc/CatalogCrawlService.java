package se.kuseman.payloadbuilder.catalog.jdbc;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.extensions.Inject;

import se.kuseman.payloadbuilder.catalog.jdbc.model.Catalog;

/** Service that crawls and caches schema information */
@Inject
public class CatalogCrawlService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogCrawlService.class);
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(BasicThreadFactory.builder()
            .daemon(true)
            .namingPattern("CatalogCrawlService-#%d")
            .build());

    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    public CatalogCrawlService()
    {
    }

    /** Load catalog for provided */
    public Catalog getCatalog(IConnectionState connectionState, String database)
    {
        if (isBlank(database))
        {
            return null;
        }

        String jdbcURL = connectionState.getJdbcConnection()
                .getJdbcURL();
        CacheKey key = new CacheKey(jdbcURL, database);
        return cache.compute(key, (k, v) ->
        {
            boolean load = false;
            if (v == null)
            {
                v = new CacheEntry();
                v.expireTime = System.currentTimeMillis() + Duration.ofMinutes(10)
                        .toMillis();
                load = true;
            }
            else if (v.expired())
            {
                load = true;
            }

            CacheEntry entry = v;
            if (load
                    && !entry.loading)
            {
                entry.loading = true;
                EXECUTOR.execute(() ->
                {
                    try
                    {
                        entry.catalog = connectionState.getJdbcDialect()
                                .getCatalog(connectionState, database);
                        entry.expireTime = System.currentTimeMillis() + Duration.ofMinutes(10)
                                .toMillis();
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Error crawling database {}", database, e);
                    }
                    finally
                    {
                        entry.loading = false;
                    }
                });
            }

            return entry;
        }).catalog;
    }

    private record CacheKey(String jdbcUrl, String database)
    {
    }

    private static class CacheEntry
    {
        private volatile Catalog catalog;
        private volatile boolean loading;
        private long expireTime;

        boolean expired()
        {
            return expireTime > 0
                    && System.currentTimeMillis() >= expireTime;
        }
    }
}
