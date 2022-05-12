package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
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

/**
 * Model for {@link JdbcCatalogExtension}'s connections
 */
@Inject
class JdbcConnectionsModel extends AbstractListModel<JdbcConnectionsModel.Connection>
{
    private final List<Connection> connections = new ArrayList<>();
    /** {@link JdbcCatalogExtension} register it's reload button here so they all can be disabled upon load to avoid multiple reloads simultaneously */
    private final List<JButton> reloadButtons = new ArrayList<>();

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

        @JsonIgnore
        private String username = System.getProperty("user.name");
        @JsonIgnore
        private char[] password;
        @JsonIgnore
        private List<String> databases = emptyList();

        Connection()
        {
        }

        Connection(Connection source)
        {
            this.name = source.name;
            this.type = source.type;
            this.sqlServerProperties = source.sqlServerProperties != null ? new SqlServerProperties(source.sqlServerProperties)
                    : null;
            this.username = source.username;
            this.password = ArrayUtils.clone(source.password);
            this.databases = new ArrayList<>(source.databases);
        }

        Connection(String name, SqlType type)
        {
            this.name = name;
            this.type = type;
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
                order = 3,
                title = "Raw JDBC",
                visibleAware = true)
        public JdbcProperties getJdbcProperties()
        {
            return jdbcProperties;
        }

        public void setRawJdbcProperties(JdbcProperties jdbcProperties)
        {
            this.jdbcProperties = jdbcProperties;
        }

        @Property(
                order = 4,
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

        char[] getPassword()
        {
            return password;
        }

        void setPassword(char[] password)
        {
            this.password = password;
        }

        List<String> getDatabases()
        {
            return databases;
        }

        void setDatabases(List<String> databases)
        {
            this.databases = databases;
        }

        @Override
        public boolean visible(String property)
        {
            if ("rawJdbcProperties".equals(property))
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
                    && !ArrayUtils.isEmpty(password);
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
