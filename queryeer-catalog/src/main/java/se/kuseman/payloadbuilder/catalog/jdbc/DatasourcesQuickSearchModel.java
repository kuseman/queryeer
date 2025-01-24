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
import javax.swing.JWindow;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.DialogUtils.IQuickSearchModel;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DatabaseProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

/** Model for handling quick search of datasources. */
@Inject
class DatasourcesQuickSearchModel implements IQuickSearchModel<DatasourcesQuickSearchModel.DatasourceItem>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasourcesQuickSearchModel.class);
    private final JdbcConnectionsModel connectionsModel;
    private final IQueryFileProvider queryFileProvider;
    private final DatabaseProvider databaseProvider;
    private final Map<JdbcConnection, JdbcConnectionLoadInfo> infoMap = new WeakHashMap<>();
    private final Icons icons;

    DatasourcesQuickSearchModel(JdbcConnectionsModel connectionsModel, IQueryFileProvider queryFileProvider, DatabaseProvider databaseProvider, Icons icons)
    {
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.databaseProvider = requireNonNull(databaseProvider, "databaseProvider");
        this.icons = requireNonNull(icons, "icons");
    }

    @Override
    public void handleSelection(JWindow popup, DatasourceItem item)
    {
        IQueryFile file = queryFileProvider.getCurrentFile();
        if (file == null)
        {
            popup.setVisible(false);
            return;
        }

        JdbcEngineState state = file.getEngineState();
        // Close any existing state
        EXECUTOR.execute(() -> IOUtils.closeQuietly(state));

        // Get the dialect from the connection URL
        JdbcDatabase jdbcDatabase = databaseProvider.getDatabase(item.connection.getJdbcURL());
        ConnectionState newState = new ConnectionState(item.connection, jdbcDatabase, item.database);
        state.setConnectionState(newState);
        state.getQueryEngine()
                .focus(file);
        popup.setVisible(false);
    }

    @Override
    public void reload(Consumer<DatasourceItem> itemConsumer)
    {
        int size = connectionsModel.getSize();
        for (int i = 0; i < size; i++)
        {
            JdbcConnection connection = connectionsModel.getElementAt(i);
            if (!connection.isEnabled())
            {
                continue;
            }
            // Only include connections that actual has credentials
            // we cannot popup and ask for password for each that lacks

            boolean silent = true;
            // This is a connection with an encrypted password => prepare non silent to trigger decrypt
            if (connection.getRuntimePassword() == null
                    && !isBlank(connection.getPassword()))
            {
                silent = false;
            }

            if (connectionsModel.prepare(connection, silent)
                    && connection.hasCredentials())
            {
                List<String> databases = connection.getDatabases();
                JdbcConnectionLoadInfo loadInfo = infoMap.computeIfAbsent(connection, c -> new JdbcConnectionLoadInfo());

                // Connection is failing, stall it for a bit
                if (!loadInfo.shouldTryAgain()
                        || loadInfo.loading)
                {
                    continue;
                }

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
    }

    class DatasourceItem implements IQuickSearchModel.Item
    {
        private final JdbcConnection connection;
        private final String database;
        private final String title;

        DatasourceItem(JdbcConnection connection, String database)
        {
            this.connection = connection;
            this.database = database;
            this.title = connection.getName() + " / " + database;
        }

        @Override
        public String getTitle()
        {
            return title;
        }

        @Override
        public Icon getIcon()
        {
            return icons.database;
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
