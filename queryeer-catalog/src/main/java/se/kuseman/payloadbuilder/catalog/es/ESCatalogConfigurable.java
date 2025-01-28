package se.kuseman.payloadbuilder.catalog.es;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.queryeer.api.component.ListPropertiesComponent;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.ICryptoService;

import se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Connection;

/** ES configurable */
class ESCatalogConfigurable implements IConfigurable
{
    private static final String NAME = "se.kuseman.payloadbuilder.catalog.es.ESCatalogExtension";
    private static final String CONNECTIONS = "connections";
    private final ESConnectionsModel connectionsModel;
    private final List<Consumer<Boolean>> dirstyStateConsumers = new ArrayList<>();
    private final IConfig config;
    private final ICryptoService cryptoService;
    private ListPropertiesComponent<Connection> configComponent;

    ESCatalogConfigurable(IConfig config, ESConnectionsModel connectionsModel, ICryptoService cryptoService)
    {
        this.config = requireNonNull(config, "config");
        this.cryptoService = requireNonNull(cryptoService, "cryptoService");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        loadSettings();
    }

    ESConnectionsModel getConnectionsModel()
    {
        return connectionsModel;
    }

    @Override
    public ListPropertiesComponent<Connection> getComponent()
    {
        if (configComponent == null)
        {
            configComponent = new ListPropertiesComponent<>(Connection.class, this::notifyDirty, this::connection, this::cloneConnection);
            configComponent.init(connectionsModel.copyConnections());
        }
        return configComponent;
    }

    @Override
    public void revertChanges()
    {
        getComponent().init(connectionsModel.copyConnections());
    }

    @Override
    public void commitChanges()
    {
        connectionsModel.setConnections(getComponent().getResult());

        boolean save = true;
        // Encrypt passwords before saving
        for (Connection con : connectionsModel.getConnections())
        {
            String pass = con.getAuthPassword();
            if (isBlank(pass))
            {
                continue;
            }

            con.setRuntimeAuthPassword(pass.toCharArray());

            String encryptedPass = cryptoService.encryptString(pass);
            if (encryptedPass == null)
            {
                save = false;
                continue;
            }

            con.setAuthPassword(encryptedPass);
        }

        if (save)
        {
            config.saveExtensionConfig(NAME, singletonMap(CONNECTIONS, connectionsModel.getConnections()));
            getComponent().init(connectionsModel.copyConnections());
        }
    }

    @Override
    public EncryptionResult reEncryptSecrets(ICryptoService newCryptoService)
    {
        boolean change = false;
        for (Connection con : getComponent().getResult())
        {
            String pass = con.getAuthPassword();
            if (isBlank(pass))
            {
                continue;
            }

            if ((pass = cryptoService.decryptString(pass)) == null)
            {
                return EncryptionResult.ABORT;
            }

            con.setRuntimeAuthPassword(pass.toCharArray());
            con.setAuthPassword(newCryptoService.encryptString(pass));
            change = true;
        }

        return change ? EncryptionResult.SUCCESS
                : EncryptionResult.NO_CHANGE;
    }

    @Override
    public String getTitle()
    {
        return "Connections";
    }

    @Override
    public String getLongTitle()
    {
        return "Connections to Elasticsearch";
    }

    @Override
    public String groupName()
    {
        return "Elasticsearch";
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirstyStateConsumers.add(consumer);
    }

    @Override
    public void removeDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirstyStateConsumers.remove(consumer);
    }

    private Connection connection()
    {
        Connection connection = new Connection();
        connection.setEndpoint("http://localhost:9200");
        return connection;
    }

    private Connection cloneConnection(Connection connection)
    {
        Connection newConnection = new Connection(connection);
        newConnection.setName(StringUtils.defaultIfBlank(connection.getName(), connection.getEndpoint()) + " - Copy");
        return newConnection;
    }

    private void notifyDirty(boolean dirty)
    {
        dirstyStateConsumers.forEach(c -> c.accept(dirty));
    }

    private void loadSettings()
    {
        Map<String, Object> settings = config.loadExtensionConfig(NAME);
        List<Connection> connections = ESDatasource.MAPPER.convertValue(defaultIfNull(settings.get(CONNECTIONS), emptyList()), new TypeReference<List<Connection>>()
        {
        });
        if (connections == null)
        {
            return;
        }
        connectionsModel.setConnections(connections);
    }
}
