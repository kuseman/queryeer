package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.queryeer.api.component.ListPropertiesComponent;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.ICryptoService;

/** Jdbc configurable */
class JdbcConnectionsConfigurable implements IConfigurable
{
    private static final String NAME = "se.kuseman.payloadbuilder.catalog.jdbc.JdbcCatalogExtension";
    private static final String CONNECTIONS = "connections";
    private final JdbcConnectionsModel connectionsModel;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();
    private final IConfig config;
    private final ICryptoService cryptoService;
    private ListPropertiesComponent<JdbcConnection> configComponent;

    JdbcConnectionsConfigurable(IConfig config, JdbcConnectionsModel connectionsModel, ICryptoService cryptoService)
    {
        this.config = requireNonNull(config, "config");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        this.cryptoService = requireNonNull(cryptoService, "cryptoService");
        loadSettings();
    }

    JdbcConnectionsModel getConnectionsModel()
    {
        return connectionsModel;
    }

    @Override
    public ListPropertiesComponent<JdbcConnection> getComponent()
    {
        if (configComponent == null)
        {
            configComponent = new ListPropertiesComponent<>(JdbcConnection.class, this::notifyDirty, this::connection);
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
        for (JdbcConnection con : connectionsModel.getConnections())
        {
            String pass = con.getPassword();
            if (isBlank(pass))
            {
                continue;
            }

            String encryptedPass = cryptoService.encryptString(pass);
            if (encryptedPass == null)
            {
                save = false;
                continue;
            }

            con.setPassword(encryptedPass);
        }

        if (save)
        {
            config.saveExtensionConfig(NAME, singletonMap(CONNECTIONS, connectionsModel.getConnections()));
        }
    }

    @Override
    public EncryptionResult reEncryptSecrets(ICryptoService newCryptoService)
    {
        boolean change = false;
        for (JdbcConnection con : getComponent().getResult())
        {
            String pass = con.getPassword();
            if (isBlank(pass))
            {
                continue;
            }

            if ((pass = cryptoService.decryptString(pass)) == null)
            {
                return EncryptionResult.ABORT;
            }

            con.setPassword(newCryptoService.encryptString(pass));
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
        return "JDBC Connections";
    }

    @Override
    public String groupName()
    {
        return Constants.TITLE;
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyStateConsumers.add(consumer);
    }

    @Override
    public void removeDirtyStateConsumer(Consumer<Boolean> consumer)
    {
        dirtyStateConsumers.remove(consumer);
    }

    private JdbcConnection connection()
    {
        JdbcConnection connection = new JdbcConnection();
        connection.setName("New Connection");
        return connection;
    }

    private void notifyDirty(boolean dirty)
    {
        dirtyStateConsumers.forEach(c -> c.accept(dirty));
    }

    private void loadSettings()
    {
        Map<String, Object> properties = config.loadExtensionConfig(NAME);
        List<JdbcConnection> connections = Constants.MAPPER.convertValue(properties.get(CONNECTIONS), new TypeReference<List<JdbcConnection>>()
        {
        });
        if (connections == null)
        {
            return;
        }
        connectionsModel.setConnections(connections);
    }
}
