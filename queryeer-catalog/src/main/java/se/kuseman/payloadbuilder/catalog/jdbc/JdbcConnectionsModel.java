package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JOptionPane;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.utils.CredentialUtils;
import com.queryeer.api.utils.CredentialUtils.Credentials;

import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DatabaseProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDatabase;

/**
 * Model for {@link JdbcCatalogExtension}'s connections
 */
@Inject
class JdbcConnectionsModel extends AbstractListModel<JdbcConnection>
{
    private final List<JdbcConnection> connections = new ArrayList<>();
    /** {@link JdbcCatalogExtension} register it's reload button here so they all can be disabled upon load to avoid multiple reloads simultaneously */
    private final List<JButton> reloadButtons = new ArrayList<>();
    private final ICryptoService cryptoService;
    private final IQueryFileProvider queryFileProvider;
    private final DatabaseProvider databaseProvider;

    JdbcConnectionsModel(ICryptoService cryptoService, IQueryFileProvider queryFileProvider, DatabaseProvider databaseProvider)
    {
        this.cryptoService = requireNonNull(cryptoService, "cryptoService");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.databaseProvider = requireNonNull(databaseProvider, "databaseProvider");
    }

    void registerReloadButton(JButton button)
    {
        reloadButtons.add(button);
    }

    void setEnableRealod(boolean b)
    {
        reloadButtons.forEach(btn -> btn.setEnabled(b));
    }

    @Override
    public int getSize()
    {
        return connections.size();
    }

    @Override
    public JdbcConnection getElementAt(int index)
    {
        return connections.get(index);
    }

    /** Return a deep copy of connections */
    List<JdbcConnection> copyConnections()
    {
        return connections.stream()
                .map(JdbcConnection::new)
                .collect(toList());
    }

    List<JdbcConnection> getConnections()
    {
        return connections;
    }

    void setConnections(List<JdbcConnection> connections)
    {
        this.connections.clear();
        this.connections.addAll(connections);
        fireContentsChanged(this, 0, getSize() - 1);
    }

    /** Finds a connection in model from session properties */
    JdbcConnection findConnection(IQuerySession querySession, String catalogAlias)
    {
        String url = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.URL)
                .valueAsString(0);

        int size = getSize();
        for (int i = 0; i < size; i++)
        {
            JdbcConnection connection = getElementAt(i);
            if (equalsIgnoreCase(connection.getJdbcURL(), url))
            {
                return connection;
            }
        }

        return null;
    }

    protected Credentials getCredentials(String connectionDescription, String prefilledUsername, boolean readOnlyUsername)
    {
        return CredentialUtils.getCredentials(connectionDescription, prefilledUsername, readOnlyUsername);
    }

    /**
     * Prepares connection before usage. Decrypts encrypted passwords or ask for credentials if missing etc. NOTE! Pops UI dialogs if needed
     *
     * @return Returns true if connection is prepared otherwise false
     */
    boolean prepare(JdbcConnection connection, boolean silent)
    {
        if (connection == null)
        {
            return false;
        }

        // All setup
        if (connection.hasCredentials())
        {
            return true;
        }

        // Connection with empty, password => ask for it
        if (isBlank(connection.getPassword()))
        {
            if (silent)
            {
                return false;
            }

            Credentials credentials = getCredentials(connection.toString(), connection.getUsername(), true);
            if (credentials == null)
            {
                return false;
            }

            connection.setRuntimePassword(credentials.getPassword());
            // Try creating connection
            // TODO: dialog with spinner
            try (java.sql.Connection con = createConnection(connection))
            {
            }
            catch (Exception e)
            {
                IQueryFile queryFile = queryFileProvider.getCurrentFile();
                if (queryFile != null)
                {
                    e.printStackTrace(queryFile.getMessagesWriter());
                    queryFile.focusMessages();
                }
                JOptionPane.showInternalMessageDialog(null, "Could not create connection, check credentials!", "Failure", JOptionPane.ERROR_MESSAGE);
                connection.setRuntimePassword(null);
                return false;
            }

            return true;
        }

        if (silent
                && !cryptoService.isInitalized())
        {
            return false;
        }

        String decrypted = cryptoService.decryptString(connection.getPassword());
        // Failed to decrypt password
        if (decrypted == null)
        {
            return false;
        }

        connection.setRuntimePassword(decrypted.toCharArray());
        return true;
    }

    java.sql.Connection createConnection(JdbcConnection connection) throws SQLException
    {
        JdbcDatabase queryeerDatabase = databaseProvider.getDatabase(connection.getJdbcURL());
        try
        {
            return queryeerDatabase.createConnection(connection.getJdbcURL(), connection.getUsername(), new String(connection.getRuntimePassword()));
        }
        catch (Exception e)
        {
            // Clear password upon error
            connection.setRuntimePassword(null);
            throw e;
        }
    }

    /** Gets and/or loads databases for provided connection */
    List<String> getDatabases(JdbcConnection connection, String catalogAlias, boolean forceReload, boolean reThrowError)
    {
        if (!connection.hasCredentials())
        {
            return emptyList();
        }

        // Lock here to avoid that we load the database multiple times for the same connection
        synchronized (connection)
        {
            List<String> databases = connection.getDatabases();

            if (!forceReload
                    && databases != null)
            {
                return databases;
            }

            JdbcDatabase database = databaseProvider.getDatabase(connection.getJdbcURL());
            setEnableRealod(false);
            try (java.sql.Connection sqlConnection = database.createConnection(connection.getJdbcURL(), connection.getUsername(), new String(connection.getRuntimePassword())))
            {
                List<String> loadedDatabases = new ArrayList<>();
                if (database.usesSchemaAsDatabase())
                {
                    try (ResultSet rs = sqlConnection.getMetaData()
                            .getSchemas())
                    {
                        while (rs.next())
                        {
                            loadedDatabases.add(rs.getString(1));
                        }
                    }
                }
                else
                {
                    try (ResultSet rs = sqlConnection.getMetaData()
                            .getCatalogs())
                    {
                        while (rs.next())
                        {
                            loadedDatabases.add(rs.getString(1));
                        }
                    }
                }

                loadedDatabases.sort((a, b) -> a.compareTo(b));
                connection.setDatabases(loadedDatabases);
            }
            catch (Exception e)
            {
                // Set databases here to avoid loading over and over again. A force reload is needed
                connection.setDatabases(emptyList());
                // Clear runtime password to be able to let user input again upon error
                connection.setRuntimePassword(null);

                IQueryFile queryFile = queryFileProvider.getCurrentFile();
                if (queryFile != null)
                {
                    e.printStackTrace(queryFile.getMessagesWriter());
                    queryFile.focusMessages();
                }
                else
                {
                    e.printStackTrace();
                }

                if (reThrowError)
                {
                    throw new RuntimeException(e);
                }
            }
            finally
            {
                setEnableRealod(true);
            }
        }
        return connection.getDatabases();
    }
}
