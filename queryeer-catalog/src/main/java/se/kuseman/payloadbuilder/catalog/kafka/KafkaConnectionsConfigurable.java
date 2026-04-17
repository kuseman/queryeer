package se.kuseman.payloadbuilder.catalog.kafka;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
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

/** Kafka configurable. */
class KafkaConnectionsConfigurable implements IConfigurable
{
    private static final String NAME = "se.kuseman.payloadbuilder.catalog.kafka.KafkaCatalogExtension";
    private static final String CONNECTIONS = "connections";

    private final IConfig config;
    private final ICryptoService cryptoService;
    private final KafkaConnectionsModel connectionsModel;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();

    private ListPropertiesComponent<KafkaConnection> configComponent;

    KafkaConnectionsConfigurable(IConfig config, ICryptoService cryptoService, KafkaConnectionsModel connectionsModel)
    {
        this.config = requireNonNull(config, "config");
        this.cryptoService = requireNonNull(cryptoService, "cryptoService");
        this.connectionsModel = requireNonNull(connectionsModel, "connectionsModel");
        loadSettings();
    }

    @Override
    public ListPropertiesComponent<KafkaConnection> getComponent()
    {
        if (configComponent == null)
        {
            configComponent = new ListPropertiesComponent<>(KafkaConnection.class, this::notifyDirty, this::newConnection, this::cloneConnection);
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
    public boolean commitChanges()
    {
        connectionsModel.setConnections(getComponent().getResult());

        for (KafkaConnection con : connectionsModel.getConnections())
        {
            String pass = con.getSaslJaasPassword();
            if (isBlank(pass))
            {
                continue;
            }

            con.setRuntimeSaslJaasPassword(pass.toCharArray());

            String encryptedPass = cryptoService.encryptString(pass);
            if (encryptedPass == null)
            {
                return false;
            }
            con.setSaslJaasPassword(encryptedPass);
        }

        config.saveExtensionConfig(NAME, singletonMap(CONNECTIONS, connectionsModel.getConnections()));
        getComponent().init(connectionsModel.copyConnections());
        return true;
    }

    @Override
    public EncryptionResult reEncryptSecrets(ICryptoService newCryptoService)
    {
        boolean change = false;
        for (KafkaConnection con : getComponent().getResult())
        {
            String pass = con.getSaslJaasPassword();
            if (isBlank(pass))
            {
                continue;
            }

            if ((pass = cryptoService.decryptString(pass)) == null)
            {
                return EncryptionResult.ABORT;
            }

            con.setRuntimeSaslJaasPassword(pass.toCharArray());
            con.setSaslJaasPassword(newCryptoService.encryptString(pass));
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
        return "Connections to Kafka";
    }

    @Override
    public String groupName()
    {
        return KafkaConstants.TITLE;
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

    private void notifyDirty(boolean dirty)
    {
        dirtyStateConsumers.forEach(c -> c.accept(dirty));
    }

    private KafkaConnection newConnection()
    {
        KafkaConnection connection = new KafkaConnection();
        connection.setName("New Kafka Connection");
        return connection;
    }

    private KafkaConnection cloneConnection(KafkaConnection connection)
    {
        KafkaConnection newConnection = new KafkaConnection(connection);
        newConnection.setName(connection.getName() + " - Copy");
        return newConnection;
    }

    private void loadSettings()
    {
        Map<String, Object> settings = config.loadExtensionConfig(NAME);
        List<KafkaConnection> connections = KafkaConstants.MAPPER.convertValue(getIfNull(settings.get(CONNECTIONS), emptyList()), new TypeReference<List<KafkaConnection>>()
        {
        });
        if (connections == null)
        {
            return;
        }
        connectionsModel.setConnections(connections);
    }
}
