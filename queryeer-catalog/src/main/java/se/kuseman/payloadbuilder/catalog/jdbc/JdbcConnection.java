package se.kuseman.payloadbuilder.catalog.jdbc;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.component.IPropertyAware;
import com.queryeer.api.component.Property;

import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.Common;

/** Server connection. */
public class JdbcConnection implements IPropertyAware
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

    @JsonProperty
    private boolean enabled = true;

    /** Hex rgb color of this connection */
    @JsonProperty
    private String color;

    // Runtime values
    @JsonIgnore
    private List<String> databases;
    @JsonIgnore
    private char[] runtimePassword;

    JdbcConnection()
    {
    }

    JdbcConnection(JdbcConnection source)
    {
        this.name = source.name;
        this.type = source.type;
        this.sqlServerProperties = source.sqlServerProperties != null ? new SqlServerProperties(source.sqlServerProperties)
                : null;
        this.jdbcProperties = source.jdbcProperties != null ? new JdbcProperties(source.jdbcProperties)
                : null;
        this.username = source.username;
        this.password = source.password;
        this.color = source.color;
        this.runtimePassword = source.runtimePassword != null ? Arrays.copyOf(source.runtimePassword, source.runtimePassword.length)
                : null;
        this.databases = source.databases != null ? new ArrayList<>(source.databases)
                : null;
        this.enabled = source.enabled;
    }

    JdbcConnection(String name, SqlType type)
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

    @Property(
            order = 6,
            title = "RGB color",
            tooltip = "RGB color for this connection shown in status. Hex format #RRGGBB")
    public String getColor()
    {
        return color;
    }

    public void setColor(String color)
    {
        this.color = color;
    }

    @Property(
            order = 7,
            title = "Enabled")
    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    List<String> getDatabases()
    {
        return databases;
    }

    void setDatabases(List<String> databases)
    {
        this.databases = databases;
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

    /** Return resulting jdbc url for this connection. */
    @Property(
            order = -1,
            title = "url",
            ignore = true)
    public String getJdbcURL()
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

    @Property(
            order = 10,
            title = "Driver")
    public String getDriverNote()
    {
        return "Make sure to download appropriate driver and put into shared folder";
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

    /** Test connection. */
    @Property(
            order = 11,
            title = "Test Connection")
    public void testConnection()
    {
        JdbcConnectionsModel.instance.validate(this);
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

    /** Type of sql. */
    enum SqlType
    {
        JDBC_URL("JDBC URL"),
        SQLSERVER("MSSql Server");

        final String title;

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