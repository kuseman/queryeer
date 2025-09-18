package se.kuseman.payloadbuilder.catalog.es;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.Strings.CI;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.IPropertyAware;
import com.queryeer.api.component.Property;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.utils.ArrayUtils;
import com.queryeer.api.utils.CredentialUtils;
import com.queryeer.api.utils.CredentialUtils.Credentials;

import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.Common;

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
    private final ICryptoService cryptoService;

    ESConnectionsModel(IQueryFileProvider queryFileProvider, ICryptoService cryptoService)
    {
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.cryptoService = requireNonNull(cryptoService, "cryptoService");
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

    /** Finds a connection in model from session properties */
    Connection findConnection(IQuerySession querySession, String catalogAlias)
    {
        String endpoint = querySession.getCatalogProperty(catalogAlias, ESCatalog.ENDPOINT_KEY)
                .valueAsString(0);

        int size = getSize();
        for (int i = 0; i < size; i++)
        {
            Connection connection = getElementAt(i);
            if (CI.equals(connection.endpoint, endpoint))
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
     * @param silent Prepare silent ie. prepare without dialogs
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
        if (isBlank(connection.authPassword))
        {
            if (silent)
            {
                return false;
            }

            Credentials credentials = getCredentials(connection.toString(), connection.authUsername, true);
            if (credentials == null)
            {
                return false;
            }

            connection.runtimeAuthPassword = credentials.getPassword();
            return true;
        }

        if (silent
                && !cryptoService.isInitalized())
        {
            return false;
        }

        String decrypted = cryptoService.decryptString(connection.authPassword);
        // Failed to decrypt password
        if (decrypted == null)
        {
            return false;
        }
        connection.runtimeAuthPassword = decrypted.toCharArray();
        return true;
    }

    /** Get indices for provided connection. */
    @SuppressWarnings({ "unchecked" })
    List<Index> getIndices(Connection connection, boolean forceReload)
    {
        SwingUtilities.invokeLater(() -> reloadButtons.forEach(b -> b.setEnabled(false)));
        try
        {
            synchronized (connection)
            {
                List<Index> indices = connection.indices;
                // Don't try to load connections that obviously won't work
                if ((!forceReload
                        && indices != null)
                        || (!forceReload
                                && !connection.hasCredentials()))
                {
                    return getIfNull(indices, emptyList());
                }

                if (!forceReload
                        && indices != null)
                {
                    return indices;
                }

                HttpGet getIndices = new HttpGet(connection.endpoint + "/_aliases");
                try
                {
                    connection.indices = execute(connection, getIndices, response ->
                    {
                        HttpEntity entity = response.getEntity();
                        if (response.getCode() != HttpStatus.SC_OK)
                        {
                            throw new RuntimeException("Error query Elastic. " + IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8));
                        }

                        Map<String, Object> map = ESDatasource.MAPPER.readValue(entity.getContent(), Map.class);
                        List<Index> result = new ArrayList<>(map.size());

                        Set<String> seenAliases = new HashSet<>();

                        for (Entry<String, Object> e : map.entrySet())
                        {
                            result.add(new Index(e.getKey()));
                            // Collect aliases
                            if (e.getValue() instanceof Map
                                    && ((Map<String, Object>) e.getValue()).containsKey("aliases"))
                            {
                                Map<String, Object> aliasesMap = (Map<String, Object>) ((Map<String, Object>) e.getValue()).get("aliases");
                                for (String key : aliasesMap.keySet())
                                {
                                    if (seenAliases.add(key.toLowerCase()))
                                    {
                                        result.add(new Index(key, Index.Type.ALIAS));
                                    }
                                }
                            }
                        }

                        populateDataStreams(connection, result);

                        result.sort((a, b) ->
                        {
                            int c = Integer.compare(a.type.order, b.type.order);
                            if (c != 0)
                            {
                                return c;
                            }

                            return String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
                        });
                        return result;
                    });
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

                return connection.indices;
            }
        }
        finally
        {
            SwingUtilities.invokeLater(() -> reloadButtons.forEach(b -> b.setEnabled(true)));
        }
    }

    static void populateDataStreams(Connection connection, List<Index> result)
    {
        HttpGet getDataStreams = new HttpGet(connection.endpoint + "/_data_stream");
        try
        {
            execute(connection, getDataStreams, response ->
            {
                HttpEntity entity = response.getEntity();
                if (response.getCode() == 200)
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = ESDatasource.MAPPER.readValue(entity.getContent(), Map.class);

                    if (map.containsKey("data_streams"))
                    {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> streams = (List<Map<String, Object>>) map.get("data_streams");
                        for (Map<String, Object> stream : streams)
                        {
                            String name = (String) stream.get("name");
                            if (name != null)
                            {
                                result.add(new Index(name, Index.Type.DATASTREAM));
                            }
                        }
                    }
                }
                return null;
            });
        }
        catch (Exception e)
        {
            // Swallow this
        }
    }

    static <T> T execute(Connection connection, ClassicHttpRequest request, HttpClientResponseHandler<T> handler) throws IOException
    {
        return HttpClientUtils.execute(null, request, connection.trustCertificate, connection.connectTimeout, connection.receiveTimeout, connection.authType, connection.authUsername,
                connection.runtimeAuthPassword, handler);
    }

    static class Index
    {
        final String name;
        final Type type;

        Index(String name)
        {
            this(name, Type.INDEX);
        }

        Index(String name, Type type)
        {
            this.name = name;
            this.type = type;
        }

        @Override
        public String toString()
        {
            if (type == Type.INDEX)
            {
                return name;
            }

            return name + " (" + type + ")";
        }

        enum Type
        {
            INDEX(2),
            ALIAS(0),
            DATASTREAM(1);

            private final int order;

            Type(int order)
            {
                this.order = order;
            }
        }
    }

    /** Connection domain */
    static class Connection implements IPropertyAware
    {
        @JsonProperty
        private String name;
        @JsonProperty
        private String endpoint;
        @JsonProperty
        private boolean trustCertificate;
        @JsonProperty
        private Integer connectTimeout;
        @JsonProperty
        private Integer receiveTimeout;
        @JsonProperty
        private AuthType authType = AuthType.NONE;
        @JsonProperty
        private String authUsername;
        /** Stored password for this connection. Is encrypted if set */
        @JsonProperty
        private String authPassword;

        // Ignore runtime values
        @JsonIgnore
        private List<Index> indices;

        /** This is the de-crypted/entered password for this connection */
        @JsonIgnore
        private char[] runtimeAuthPassword;

        Connection()
        {
        }

        Connection(Connection source)
        {
            this.name = source.name;
            this.endpoint = source.endpoint;
            this.trustCertificate = source.trustCertificate;
            this.connectTimeout = source.connectTimeout;
            this.receiveTimeout = source.receiveTimeout;
            this.authType = source.authType;
            this.authUsername = source.authUsername;
            this.authPassword = source.authPassword;
            this.indices = source.indices != null ? new ArrayList<>(source.indices)
                    : null;

            this.runtimeAuthPassword = source.runtimeAuthPassword != null ? Arrays.copyOf(source.runtimeAuthPassword, source.runtimeAuthPassword.length)
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
            querySession.setCatalogProperty(catalogAlias, ESCatalog.AUTH_PASSWORD_KEY, runtimeAuthPassword);
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
                            && !ArrayUtils.isEmpty(runtimeAuthPassword);
                default:
                    throw new RuntimeException("Unsupported auth type " + authType);
            }
        }

        @Property(
                order = -1,
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
        public AuthType getAuthType()
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

        @Property(
                order = 6,
                title = "Auth Password",
                visibleAware = true,
                password = true,
                tooltip = Common.AUTH_PASSWORD_TOOLTIP)
        public String getAuthPassword()
        {
            return authPassword;
        }

        public void setAuthPassword(String authPassword)
        {
            this.authPassword = authPassword;
        }

        char[] getRuntimeAuthPassword()
        {
            return runtimeAuthPassword;
        }

        void setRuntimeAuthPassword(char[] runtimeAuthPassword)
        {
            this.runtimeAuthPassword = runtimeAuthPassword;
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

        List<Index> getIndices()
        {
            return indices;
        }

        void setIndices(List<Index> indices)
        {
            this.indices = indices;
        }

        @Override
        public String toString()
        {
            return Objects.toString(name, endpoint);
        }

        @Override
        public int hashCode()
        {
            return endpoint.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof Connection that)
            {
                return Objects.equals(name, that.name)
                        && Objects.equals(endpoint, that.endpoint)
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
