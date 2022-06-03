package com.queryeer.jdbc;

import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractListModel;

import org.apache.commons.lang3.ArrayUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.component.IPropertyAware;
import com.queryeer.api.component.Properties;
import com.queryeer.api.component.Property;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.Inject;

/**
 * Model for {@link JdbcCatalogExtension}'s connections
 */
@Inject
class JdbcConnectionModel extends AbstractListModel<IJdbcConnection> implements IJdbcConnectionListModel
{
    private static final String NAME = "se.kuseman.payloadbuilder.catalog.jdbc.JdbcCatalogExtension";
    private static final String CONNECTIONS = "connections";
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final IConfig config;
    private final List<IJdbcConnection> connections = new ArrayList<>();

    JdbcConnectionModel(IConfig config)
    {
        this.config = requireNonNull(config, "config");
        loadSettings();
    }

    @Override
    public int getSize()
    {
        return connections.size();
    }

    @Override
    public IJdbcConnection getElementAt(int index)
    {
        return connections.get(index);
    }

    /** Return a deep copy of connections */
    List<IJdbcConnection> copyConnections()
    {
        return connections.stream()
                .map(JdbcConnection::new)
                .collect(toList());
    }

    @Override
    public List<IJdbcConnection> getConnections()
    {
        return connections;
    }

    void setConnections(List<? extends IJdbcConnection> connections)
    {
        this.connections.clear();
        this.connections.addAll(connections);
        fireContentsChanged(this, 0, getSize() - 1);
    }

    private void loadSettings()
    {
        Map<String, Object> properties = config.loadExtensionConfig(NAME);
        List<JdbcConnection> connections = MAPPER.convertValue(properties.get(CONNECTIONS), new TypeReference<List<JdbcConnection>>()
        {
        });
        if (connections == null)
        {
            return;
        }
        setConnections(connections);
    }

    void save()
    {
        config.saveExtensionConfig(JdbcConnectionModel.NAME, singletonMap(JdbcConnectionModel.CONNECTIONS, connections));
    }

    /** Server connection. */
    @Properties(
            header = "<html><h2>JDBC Connections</h2><hr></html>")
    static class JdbcConnection implements IPropertyAware, IJdbcConnection
    {
        @JsonProperty
        private String name;
        @JsonProperty
        private JdbcType type = JdbcType.JDBC_URL;
        @JsonProperty
        private JdbcProperties jdbcProperties;
        @JsonProperty
        private SqlServerProperties sqlServerProperties;

        private String username = System.getProperty("user.name");
        @JsonIgnore
        private char[] password;

        JdbcConnection()
        {
        }

        JdbcConnection(IJdbcConnection s)
        {
            JdbcConnection source = (JdbcConnection) s;
            this.name = source.name;
            this.type = source.type;
            this.sqlServerProperties = source.sqlServerProperties != null ? new SqlServerProperties(source.sqlServerProperties)
                    : null;
            this.username = source.username;
            this.password = ArrayUtils.clone(source.password);
        }

        JdbcConnection(String name, JdbcType type)
        {
            this.name = name;
            this.type = type;
        }

        @Override
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

        @Override
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

        @Override
        @Property(
                order = 2,
                title = "Type")
        public JdbcType getType()
        {
            return type;
        }

        public void setType(JdbcType type)
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

        public void setJdbcProperties(JdbcProperties jdbcProperties)
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

        @Override
        public char[] getPassword()
        {
            return password;
        }

        @Override
        public void setPassword(char[] password)
        {
            this.password = password;
        }

        @Override
        public boolean visible(String property)
        {
            if ("jdbcProperties".equals(property))
            {
                return type == JdbcType.JDBC_URL;
            }
            else if ("sqlServerProperties".equals(property))
            {
                return type == JdbcType.SQLSERVER;
            }
            return true;
        }

        @Override
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

        @Override
        public String getJdbcDriverClassName()
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

        @Override
        public boolean hasCredentials()
        {
            return !isBlank(username)
                    && !ArrayUtils.isEmpty(password);
        }

        @Override
        public String toString()
        {
            return name + " (" + type.toString() + ")";
        }
    }
}
