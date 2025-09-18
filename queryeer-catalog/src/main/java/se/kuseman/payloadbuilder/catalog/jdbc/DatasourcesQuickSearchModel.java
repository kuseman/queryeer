package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static se.kuseman.payloadbuilder.catalog.jdbc.JdbcQueryEngine.EXECUTOR;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import javax.swing.Icon;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.DialogUtils.IQuickSearchModel;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DialectProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;

/** Model for handling quick search of datasources. */
@Inject
class DatasourcesQuickSearchModel implements IQuickSearchModel<DatasourcesQuickSearchModel.DatasourceItem>
{
    private static final String ERROR_LOADING_MESSAGE = "Error occured while loading connection. Check logs for more information.";
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasourcesQuickSearchModel.class);
    private final JdbcConnectionsModel connectionsModel;
    private final IQueryFileProvider queryFileProvider;
    private final DialectProvider dialectProvider;
    private final Map<JdbcConnection, JdbcConnectionLoadInfo> infoMap = new WeakHashMap<>();
    private final Icons icons;

    DatasourcesQuickSearchModel(JdbcConnectionsModel connectionsModel, IQueryFileProvider queryFileProvider, DialectProvider dialectProvider, Icons icons)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.dialectProvider = requireNonNull(dialectProvider, "dialectProvider");
        this.icons = requireNonNull(icons, "icons");
    }

    @Override
    public SelectionResult handleSelection(DatasourceItem item)
    {
        // A non authorized connection, ask for credentials
        if (item.database == null)
        {
            boolean prepared = connectionsModel.prepare(item.connection, false);
            return prepared ? SelectionResult.RELOAD_MODEL
                    : SelectionResult.DO_NOTHING;
        }

        IQueryFile file = queryFileProvider.getCurrentFile();
        if (file == null)
        {
            return SelectionResult.HIDE_WINDOW;
        }

        JdbcEngineState state = file.getEngineState();
        // Close any existing state
        EXECUTOR.execute(() -> IOUtils.closeQuietly(state));

        // Get the dialect from the connection URL
        JdbcDialect jdbcDialect = dialectProvider.getDialect(item.connection.getJdbcURL());
        ConnectionState newState = new ConnectionState(item.connection, jdbcDialect, item.database);
        state.setConnectionState(newState);
        state.getQueryEngine()
                .focus(file);

        return SelectionResult.HIDE_WINDOW;
    }

    @Override
    public void reload(Consumer<DatasourceItem> itemConsumer)
    {
        int size = connectionsModel.getSize();
        // First add all non-authorized connections on top
        for (int i = 0; i < size; i++)
        {
            JdbcConnection connection = connectionsModel.getElementAt(i);
            if (!connection.isEnabled())
            {
                continue;
            }

            JdbcConnectionLoadInfo loadInfo = infoMap.computeIfAbsent(connection, c -> new JdbcConnectionLoadInfo());
            loadInfo.prepared = connectionsModel.prepare(connection, true);

            // Non authorized connection, return without db
            if (!loadInfo.prepared)
            {
                itemConsumer.accept(new DatasourceItem(connection, null));
            }
        }

        // Then we add all authorized connections along with all their db's
        for (int i = 0; i < size; i++)
        {
            JdbcConnection connection = connectionsModel.getElementAt(i);
            if (!connection.isEnabled())
            {
                continue;
            }

            JdbcConnectionLoadInfo loadInfo = infoMap.computeIfAbsent(connection, c -> new JdbcConnectionLoadInfo());
            if (!loadInfo.prepared)
            {
                continue;
            }

            boolean loading = loadInfo.loading;
            // Connection is failing, stall it for a bit
            if (!loadInfo.shouldTryAgain()
                    || loading)
            {
                // Return a connection with a warning icon to indicate that there were errors
                // during load
                if (!loading)
                {
                    itemConsumer.accept(new DatasourceItem(connection, null, ERROR_LOADING_MESSAGE));
                }

                continue;
            }

            List<String> databases = connection.getDatabases();
            boolean reloadWithExistingDbs = loadInfo.forceReload()
                    && !CollectionUtils.isEmpty(databases);

            // We don't have any db's loaded or a force reload is needed
            if (CollectionUtils.isEmpty(databases)
                    || loadInfo.forceReload())
            {
                loadInfo.loading = true;
                EXECUTOR.submit(() ->
                {
                    try
                    {
                        loadInfo.lastLoadTime = System.currentTimeMillis();
                        List<String> databasesInner = connectionsModel.getDatabases(connection, true, true, false);
                        for (String database : databasesInner)
                        {
                            itemConsumer.accept(new DatasourceItem(connection, database));
                        }
                        loadInfo.lastFailTime = -1;
                    }
                    catch (Exception e)
                    {
                        loadInfo.lastFailTime = System.currentTimeMillis();
                        // Add a failure node
                        itemConsumer.accept(new DatasourceItem(connection, null, ERROR_LOADING_MESSAGE));
                        LOGGER.error("Error loading databases for: {}", connection.getName(), e);
                    }
                    finally
                    {
                        loadInfo.loading = false;
                    }
                });
                continue;
            }

            // Do not add old databases when there is a submited reload
            // else we will end up with duplicated ones
            if (!reloadWithExistingDbs)
            {
                for (String database : databases)
                {
                    itemConsumer.accept(new DatasourceItem(connection, database));
                }
            }
        }
    }

    class DatasourceItem implements IQuickSearchModel.Item
    {
        private final JdbcConnection connection;
        private final String database;
        private final String title;
        private final String failureMessage;

        DatasourceItem(JdbcConnection connection, String database)
        {
            this(connection, database, null);
        }

        DatasourceItem(JdbcConnection connection, String database, String failureMessage)
        {
            this.connection = connection;
            this.database = database;
            this.failureMessage = failureMessage;
            this.title = connection.getName() + (database != null ? (" / " + database)
                    : "");
        }

        @Override
        public String getTitle()
        {
            return title;
        }

        @Override
        public String getTooltip()
        {
            return failureMessage;
        }

        @Override
        public Icon getIcon()
        {
            return icons.database;
        }

        @Override
        public Icon getStatusIcon()
        {
            if (!isBlank(failureMessage))
            {
                return icons.warningIcon;
            }
            if (!connection.hasCredentials())
            {
                return icons.lock;
            }
            return null;
        }
    }

    /** Info about connection loading failure. */
    static class JdbcConnectionLoadInfo
    {
        private static final long FAIL_DELAY = Duration.ofSeconds(60)
                .toMillis();
        private static final long FORCE_LOAD_DELAY = Duration.ofMinutes(5)
                .toMillis();

        volatile long lastLoadTime = -1;
        volatile long lastFailTime = -1;
        volatile boolean loading;
        boolean prepared = false;

        boolean shouldTryAgain()
        {
            if (lastFailTime < 0)
            {
                return true;
            }
            // If failed then dealy a bit until we try again to avoid spam in logs
            return System.currentTimeMillis() - lastFailTime > FAIL_DELAY;
        }

        boolean forceReload()
        {
            if (lastLoadTime < 0)
            {
                return true;
            }
            // We force reload db's at intervals
            return System.currentTimeMillis() - lastLoadTime > FORCE_LOAD_DELAY;
        }
    }
}
