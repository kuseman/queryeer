package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.JButton;

import org.apache.commons.lang3.ArrayUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.component.IPropertyAware;
import com.queryeer.api.component.Properties;
import com.queryeer.api.component.Property;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.api.utils.CredentialUtils;
import com.queryeer.api.utils.CredentialUtils.Credentials;

import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.Common;

/**
 * Model for {@link JdbcCatalogExtension}'s connections
 */
@Inject
class JdbcConnectionsModel extends AbstractListModel<JdbcConnectionsModel.Connection>
{
    private final List<Connection> connections = new ArrayList<>();
    /** {@link JdbcCatalogExtension} register it's reload button here so they all can be disabled upon load to avoid multiple reloads simultaneously */
    private final List<JButton> reloadButtons = new ArrayList<>();
    private final ICryptoService cryptoService;

    JdbcConnectionsModel(ICryptoService cryptoService)
    {
        this.cryptoService = requireNonNull(cryptoService, "cryptoService");
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
    public Connection getElementAt(int index)
    {
        return connections.get(index);
    }

    /** Return a deep copy of connections */
    List<Connection> copyConnections()
    {
        return connections.stream()
                .map(Connection::new)
                .collect(toList());
    }

    List<Connection> getConnections()
    {
        return connections;
    }

    void setConnections(List<Connection> connections)
    {
        this.connections.clear();
        this.connections.addAll(connections);
        fireContentsChanged(this, 0, getSize() - 1);
    }

    /** Finds a connection in model from session properties */
    Connection findConnection(IQuerySession querySession, String catalogAlias)
    {
        String url = querySession.getCatalogProperty(catalogAlias, JdbcCatalog.URL)
                .valueAsString(0);

        int size = getSize();
        for (int i = 0; i < size; i++)
        {
            Connection connection = getElementAt(i);
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
    boolean prepare(Connection connection, boolean silent)
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
        if (isBlank(connection.password))
        {
            if (silent)
            {
                return false;
            }

            Credentials credentials = getCredentials(connection.toString(), connection.username, true);
            if (credentials == null)
            {
                return false;
            }

            connection.runtimePassword = credentials.getPassword();
            return true;
        }

        if (silent
                && !cryptoService.isInitalized())
        {
            return false;
        }

        String decrypted = cryptoService.decryptString(connection.password);
        // Failed to decrypt password
        if (decrypted == null)
        {
            return false;
        }
        connection.runtimePassword = decrypted.toCharArray();
        return true;
    }

    /** Server connection. */
    @Properties(
            header = "<html><h2>JDBC Connections</h2><hr></html>")
    static class Connection implements IPropertyAware
    {
        @JsonProperty
        private String name;
        @JsonProperty
        private SqlType type = SqlType.JDBC_URL;
        private JdbcProperties jdbcProperties;
        private SqlServerProperties sqlServerProperties;

        @JsonProperty
        private String username = System.getProperty("user.name");
        @JsonProperty
        private String password;

        // Runtime values
        @JsonIgnore
        private List<String> databases = emptyList();
        @JsonIgnore
        private boolean usesSchemas = false;
        @JsonIgnore
        private char[] runtimePassword;

        Connection()
        {
        }

        Connection(Connection source)
        {
            this.name = source.name;
            this.type = source.type;
            this.sqlServerProperties = source.sqlServerProperties != null ? new SqlServerProperties(source.sqlServerProperties)
                    : null;
            this.jdbcProperties = source.jdbcProperties != null ? new JdbcProperties(source.jdbcProperties)
                    : null;
            this.username = source.username;
            this.password = source.password;
            this.runtimePassword = source.runtimePassword != null ? Arrays.copyOf(source.runtimePassword, source.runtimePassword.length)
                    : null;
            this.databases = new ArrayList<>(source.databases);
        }

        Connection(String name, SqlType type)
        {
            this.name = name;
            this.type = type;
        }

        void setup(IQuerySession querySession, String catalogAlias)
        {
            querySession.setCatalogProperty(catalogAlias, JdbcCatalog.URL, getJdbcURL());
            querySession.setCatalogProperty(catalogAlias, JdbcCatalog.DRIVER_CLASSNAME, getJdbcDriverClassName());
            querySession.setCatalogProperty(catalogAlias, JdbcCatalog.USERNAME, username);
            querySession.setCatalogProperty(catalogAlias, JdbcCatalog.PASSWORD, runtimePassword);
        }

        @Property(
                order = 0,
                title = "Name")
        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        @Property(
                order = 1,
                title = "Username")
        public String getUsername()
        {
            return username;
        }

        public void setUsername(String username)
        {
            this.username = username;
        }

        @Property(
                order = 2,
                title = "Password",
                password = true,
                tooltip = Common.AUTH_PASSWORD_TOOLTIP)
        public String getPassword()
        {
            return password;
        }

        public void setPassword(String password)
        {
            this.password = password;
        }

        @Property(
                order = 3,
                title = "Type")
        public SqlType getType()
        {
            return type;
        }

        public void setType(SqlType type)
        {
            this.type = type;
        }

        @Property(
                order = 4,
                title = "Raw JDBC",
                visibleAware = true)
        public JdbcProperties getJdbcProperties()
        {
            return jdbcProperties;
        }

        public void setJdbcProperties(JdbcProperties jdbcProperties)
        {
            this.jdbcProperties = jdbcProperties;
        }

        @Property(
                order = 5,
                title = "SQL Server Properties",
                visibleAware = true)
        public SqlServerProperties getSqlServerProperties()
        {
            return sqlServerProperties;
        }

        public void setSqlServerProperties(SqlServerProperties sqlServerProperties)
        {
            this.sqlServerProperties = sqlServerProperties;
        }

        List<String> getDatabases()
        {
            return databases;
        }

        void setDatabases(List<String> databases)
        {
            this.databases = databases;
        }

        void setUsesSchemas(boolean usesSchemas)
        {
            this.usesSchemas = usesSchemas;
        }

        boolean isUsesSchemas()
        {
            return usesSchemas;
        }

        char[] getRuntimePassword()
        {
            return runtimePassword;
        }

        void setRuntimePassword(char[] runtimePassword)
        {
            this.runtimePassword = runtimePassword;
        }

        @Override
        public boolean visible(String property)
        {
            if ("jdbcProperties".equals(property))
            {
                return type == SqlType.JDBC_URL;
            }
            else if ("sqlServerProperties".equals(property))
            {
                return type == SqlType.SQLSERVER;
            }
            return true;
        }

        String getJdbcURL()
        {
            switch (type)
            {
                case JDBC_URL:
                    return jdbcProperties != null ? jdbcProperties.getUrl()
                            : "";
                case SQLSERVER:
                    return sqlServerProperties != null ? sqlServerProperties.getJdbcUrl()
                            : "";
                default:
                    throw new IllegalArgumentException("Unknown type " + type);
            }
        }

        String getJdbcDriverClassName()
        {
            switch (type)
            {
                case JDBC_URL:
                    return jdbcProperties != null ? jdbcProperties.getClassName()
                            : "";
                case SQLSERVER:
                    return sqlServerProperties != null ? sqlServerProperties.getJdbcDriverClassName()
                            : "";
                default:
                    throw new IllegalArgumentException("Unknown type " + type);
            }
        }

        boolean hasCredentials()
        {
            return !isBlank(username)
                    && !ArrayUtils.isEmpty(runtimePassword);
        }

        @Override
        public String toString()
        {
            return name + " (" + type.title + ")";
        }
    }

    /** Type of sql. */
    enum SqlType
    {
        JDBC_URL("JDBC URL"),
        SQLSERVER("MSSql Server");

        private final String title;

        SqlType(String title)
        {
            this.title = title;
        }

        String getTitle()
        {
            return title;
        }

        @Override
        public String toString()
        {
            return title;
        }
    }
}
