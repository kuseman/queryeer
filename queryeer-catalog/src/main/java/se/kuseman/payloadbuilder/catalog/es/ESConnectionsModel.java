package se.kuseman.payloadbuilder.catalog.es;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static se.kuseman.payloadbuilder.catalog.es.ESOperator.MAPPER;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.AbstractListModel;
import javax.swing.JButton;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.IPropertyAware;
import com.queryeer.api.component.Properties;
import com.queryeer.api.component.Property;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.session.IQuerySession;
import se.kuseman.payloadbuilder.catalog.es.HttpClientUtils.AuthType;

/**
 * Model for {@link ESCatalogExtension}'s connections
 */
@Inject
class ESConnectionsModel extends AbstractListModel<ESConnectionsModel.Connection>
{
    private final IQueryFileProvider queryFileProvider;
    private final List<Connection> connections = new ArrayList<>();
    /** {@link ESCatalogExtension} register it's reload button here so they all can be disabled upon load to avoid multiple reloads simultaneously */
    private final List<JButton> reloadButtons = new ArrayList<>();

    ESConnectionsModel(IQueryFileProvider queryFileProvider)
    {
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
    }

    void registerReloadButton(JButton button)
    {
        reloadButtons.add(button);
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

    /** Get indices for provided connection. */
    @SuppressWarnings("unchecked")
    List<String> getIndices(Connection connection, boolean forceReload)
    {
        reloadButtons.forEach(b -> b.setEnabled(false));
        try
        {
            synchronized (connection)
            {
                List<String> indices = connection.indices;
                // Don't try to load connections that obviously won't work
                if ((!forceReload
                        && indices != null)
                        || (!forceReload
                                && !connection.hasCredentials()))
                {
                    return defaultIfNull(indices, emptyList());
                }

                if (!forceReload
                        && indices != null)
                {
                    return indices;
                }

                HttpGet getIndices = new HttpGet(connection.endpoint + "/_aliases");
                HttpEntity entity = null;
                try (CloseableHttpResponse response = execute(connection, getIndices))
                {
                    entity = response.getEntity();
                    if (response.getStatusLine()
                            .getStatusCode() != HttpStatus.SC_OK)
                    {
                        throw new RuntimeException("Error query Elastic. " + IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8));
                    }
                    connection.indices = new ArrayList<>(MAPPER.readValue(entity.getContent(), Map.class)
                            .keySet());
                    connection.indices.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a, b));
                }
                catch (Exception e)
                {
                    // When force reloading then don't swallow catalog exceptions so it properly
                    // propagates
                    if (forceReload
                            && e instanceof CatalogException)
                    {
                        throw (CatalogException) e;
                    }

                    // Set indices here to avoid loading over and over again. A force reload is needed
                    connection.indices = emptyList();

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
                finally
                {
                    EntityUtils.consumeQuietly(entity);
                }
                return connection.indices;
            }
        }
        finally
        {
            reloadButtons.forEach(b -> b.setEnabled(true));
        }
    }

    static CloseableHttpResponse execute(Connection connection, HttpRequestBase request) throws IOException
    {
        return HttpClientUtils.execute(null, request, connection.trustCertificate, connection.connectTimeout, connection.receiveTimeout, connection.authType, connection.authUsername,
                connection.authPassword);
    }

    /** Connection domain */
    @Properties(
            header = "<html><h2>Connections to Elasticsearch</h2><hr></html>")
    static class Connection implements IPropertyAware
    {
        @JsonProperty
        String endpoint;
        @JsonProperty
        boolean trustCertificate;
        @JsonProperty
        Integer connectTimeout;
        @JsonProperty
        Integer receiveTimeout;
        @JsonProperty
        AuthType authType = AuthType.NONE;
        @JsonProperty
        String authUsername;

        // Runtime data that is not persisted
        char[] authPassword;
        List<String> indices;

        Connection()
        {
        }

        Connection(Connection source)
        {
            this.endpoint = source.endpoint;
            this.trustCertificate = source.trustCertificate;
            this.connectTimeout = source.connectTimeout;
            this.receiveTimeout = source.receiveTimeout;
            this.authType = source.authType;
            this.authUsername = source.authUsername;
            this.authPassword = ArrayUtils.clone(source.authPassword);
            this.indices = source.indices != null ? new ArrayList<>(source.indices)
                    : null;
        }

        void setup(IQuerySession querySession, String catalogAlias)
        {
            querySession.setCatalogProperty(catalogAlias, ESCatalog.ENDPOINT_KEY, endpoint);
            querySession.setCatalogProperty(catalogAlias, ESCatalog.TRUSTCERTIFICATE_KEY, trustCertificate);
            querySession.setCatalogProperty(catalogAlias, ESCatalog.CONNECT_TIMEOUT_KEY, connectTimeout);
            querySession.setCatalogProperty(catalogAlias, ESCatalog.RECEIVE_TIMEOUT_KEY, receiveTimeout);
            querySession.setCatalogProperty(catalogAlias, ESCatalog.AUTH_TYPE_KEY, authType.toString());
            querySession.setCatalogProperty(catalogAlias, ESCatalog.AUTH_USERNAME_KEY, authUsername);
            querySession.setCatalogProperty(catalogAlias, ESCatalog.AUTH_PASSWORD_KEY, authPassword);
        }

        boolean hasCredentials()
        {
            if (authType == AuthType.NONE)
            {
                return true;
            }

            switch (authType)
            {
                case BASIC:
                    return !isBlank(authUsername)
                            && !ArrayUtils.isEmpty(authPassword);
                default:
                    throw new RuntimeException("Unsupported auth type " + authType);
            }
        }

        @Property(
                order = 0,
                title = "Endpoint")
        public String getEndpoint()
        {
            return endpoint;
        }

        public void setEndpoint(String endpoint)
        {
            this.endpoint = endpoint;
        }

        @Property(
                order = 1,
                title = "Trust Certificate")
        public boolean isTrustCertificate()
        {
            return trustCertificate;
        }

        public void setTrustCertificate(boolean trustCertificate)
        {
            this.trustCertificate = trustCertificate;
        }

        @Property(
                order = 2,
                title = "Connect Timeout (ms)")
        public Integer getConnectTimeout()
        {
            return connectTimeout;
        }

        public void setConnectTimeout(Integer connectTimeout)
        {
            this.connectTimeout = connectTimeout;
        }

        @Property(
                order = 3,
                title = "Receive Timeout (ms)")
        public Integer getReceiveTimeout()
        {
            return receiveTimeout;
        }

        public void setReceiveTimeout(Integer receiveTimeout)
        {
            this.receiveTimeout = receiveTimeout;
        }

        @Property(
                order = 4,
                title = "Auth")
        public HttpClientUtils.AuthType getAuthType()
        {
            return authType;
        }

        public void setAuthType(AuthType authType)
        {
            this.authType = authType;
        }

        @Property(
                order = 5,
                title = "Auth Username",
                visibleAware = true)
        public String getAuthUsername()
        {
            return authUsername;
        }

        public void setAuthUsername(String authUsername)
        {
            this.authUsername = authUsername;
        }

        @Override
        public boolean visible(String property)
        {
            if ("authUsername".equals(property))
            {
                return authType == AuthType.BASIC;
            }
            return true;
        }

        @Override
        public String toString()
        {
            return endpoint;
        }

        @Override
        public int hashCode()
        {
            return endpoint.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof Connection)
            {
                Connection that = (Connection) obj;
                return endpoint.equals(that.endpoint)
                        && trustCertificate == that.trustCertificate
                        && Objects.equals(authType, that.authType)
                        && Objects.equals(authUsername, that.authUsername)
                        && Objects.equals(connectTimeout, that.connectTimeout)
                        && Objects.equals(receiveTimeout, that.receiveTimeout);
            }
            return false;
        }

        static Connection of(String endpoint)
        {
            Connection connection = new Connection();
            connection.endpoint = endpoint;
            return connection;
        }
    }
}
