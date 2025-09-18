package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.Strings.CI;

import java.awt.Window;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.utils.CredentialUtils;
import com.queryeer.api.utils.CredentialUtils.Credentials;
import com.queryeer.api.utils.CredentialUtils.ValidationHandler;

import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.DialectProvider;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;

/**
 * Model for {@link JdbcCatalogExtension}'s connections
 */
@Inject
class JdbcConnectionsModel extends AbstractListModel<JdbcConnection>
{
    /**
     * An ugly fix to be able to have a test button in JdbcConnection properties dialog. That dialog is built using a meta framework that introspects a POJO and we cannot inject services into that.
     */
    static JdbcConnectionsModel instance;

    private final List<JdbcConnection> connections = new ArrayList<>();
    /** {@link JdbcCatalogExtension} register it's reload button here so they all can be disabled upon load to avoid multiple reloads simultaneously */
    private final List<JButton> reloadButtons = new ArrayList<>();
    private final ICryptoService cryptoService;
    private final IQueryFileProvider queryFileProvider;
    private final DialectProvider dialectProvider;

    JdbcConnectionsModel(ICryptoService cryptoService, IQueryFileProvider queryFileProvider, DialectProvider dialectProvider)
    {
        this.cryptoService = requireNonNull(cryptoService, "cryptoService");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.dialectProvider = requireNonNull(dialectProvider, "dialectProvider");
        if (instance != null)
        {
            throw new IllegalArgumentException("JdbcConnectionsModel should only be instantiated once");
        }
        instance = this;
    }

    void registerReloadButton(JButton button)
    {
        reloadButtons.add(button);
    }

    void setEnableRealod(boolean b)
    {
        SwingUtilities.invokeLater(() -> reloadButtons.forEach(btn -> btn.setEnabled(b)));
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
            if (CI.equals(connection.getJdbcURL(), url))
            {
                return connection;
            }
        }

        return null;
    }

    protected Credentials getCredentials(String connectionDescription, String prefilledUsername, boolean readOnlyUsername, ValidationHandler validationHandler)
    {
        return CredentialUtils.getCredentials(connectionDescription, prefilledUsername, readOnlyUsername, validationHandler);
    }

    protected void validate(JdbcConnection connection)
    {
        String url = connection.getJdbcURL();
        String username = connection.getUsername();

        Window activeWindow = javax.swing.FocusManager.getCurrentManager()
                .getActiveWindow();

        // NOTE! We set password to a non empty string to not trigger
        // empty password in CredentialsUtils which would otherwise make the dialog not to return success.
        String password = "  ";
        boolean readOnlyPassword = true;
        // Some connections has credentials even without username/password (Windows Native Auth for SQLServer for example.)
        if (!connection.hasCredentials())
        {
            if (isBlank(url)
                    || isBlank(username))
            {
                JOptionPane.showMessageDialog(activeWindow, "URL and Username must be set to test connection", "Enter URL/Username", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            password = connection.getPassword();
            // Password less connection, ask for password
            if (isBlank(password))
            {
                readOnlyPassword = false;
            }
            // .. else decrypt it
            else
            {
                password = cryptoService.decryptString(password);
                if (isBlank(password))
                {
                    return;
                }
            }
        }

        if (CredentialUtils.getCredentials(connection.toString(), username, password, true, readOnlyPassword, getValidationHandler(connection)) != null)
        {
            JOptionPane.showMessageDialog(activeWindow, "Connection to " + connection.getName() + " established!", "Test Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private ValidationHandler getValidationHandler(JdbcConnection connection)
    {
        return new ValidationHandler()
        {
            private String message;
            private AtomicReference<char[]> prevRuntimePassword = new AtomicReference<>();

            @Override
            public boolean validate(String username, char[] password)
            {
                prevRuntimePassword.set(connection.getRuntimePassword());
                connection.setRuntimePassword(password);
                try (java.sql.Connection con = createConnection(connection))
                {
                    return true;
                }
                catch (Exception e)
                {
                    message = e.getMessage();
                    return false;
                }
                finally
                {
                    // Restore previous password even if success to set it the correct way after dialog is closed
                    connection.setRuntimePassword(prevRuntimePassword.get());
                }
            }

            @Override
            public void validationCanceled()
            {
                connection.setRuntimePassword(prevRuntimePassword.get());
            }

            @Override
            public String getFailureMessage()
            {
                return message;
            }
        };
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

            Credentials credentials = getCredentials(connection.toString(), connection.getUsername(), true, getValidationHandler(connection));
            if (credentials == null)
            {
                return false;
            }

            connection.setRuntimePassword(credentials.getPassword());
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
        JdbcDialect jdbcDialect = dialectProvider.getDialect(connection.getJdbcURL());
        try
        {
            String password = connection.getRuntimePassword() != null ? new String(connection.getRuntimePassword())
                    : "";
            return jdbcDialect.createConnection(connection.getJdbcURL(), connection.getUsername(), password);
        }
        catch (Exception e)
        {
            // Clear password upon error
            connection.setRuntimePassword(null);
            throw e;
        }
    }

    List<String> getDatabases(JdbcConnection connection, boolean forceReload, boolean reThrowError)
    {
        return getDatabases(connection, forceReload, reThrowError, true);
    }

    /** Gets and/or loads databases for provided connection */
    List<String> getDatabases(JdbcConnection connection, boolean forceReload, boolean reThrowError, boolean printError)
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

            char[] runtimePassword = connection.getRuntimePassword();
            if (!connection.hasCredentials())
            {
                return emptyList();
            }

            String password = runtimePassword == null ? ""
                    : new String(runtimePassword);
            JdbcDialect dialect = dialectProvider.getDialect(connection.getJdbcURL());
            setEnableRealod(false);
            try (java.sql.Connection sqlConnection = dialect.createConnection(connection.getJdbcURL(), connection.getUsername(), password))
            {
                List<String> loadedDatabases = new ArrayList<>();
                if (dialect.usesSchemaAsDatabase())
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

                if (printError)
                {
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
