package se.kuseman.payloadbuilder.catalog.kafka;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.Strings.CI;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.utils.CredentialUtils;
import com.queryeer.api.utils.CredentialUtils.Credentials;

import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.api.execution.ValueVector;

/** Model for {@link KafkaCatalogExtension}'s connections. */
@Inject
class KafkaConnectionsModel extends AbstractListModel<KafkaConnection>
{
    private final IQueryFileProvider queryFileProvider;
    private final ICryptoService cryptoService;
    private final List<KafkaConnection> connections = new ArrayList<>();
    private final List<JButton> reloadButtons = new ArrayList<>();

    KafkaConnectionsModel(IQueryFileProvider queryFileProvider, ICryptoService cryptoService)
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
    public KafkaConnection getElementAt(int index)
    {
        return connections.get(index);
    }

    List<KafkaConnection> copyConnections()
    {
        return connections.stream()
                .map(KafkaConnection::new)
                .collect(toList());
    }

    List<KafkaConnection> getConnections()
    {
        return connections;
    }

    void setConnections(List<KafkaConnection> connections)
    {
        this.connections.clear();
        this.connections.addAll(connections);
        fireContentsChanged(this, 0, getSize() - 1);
    }

    KafkaConnection findConnection(IQuerySession querySession, String catalogAlias)
    {
        ValueVector property = querySession.getCatalogProperty(catalogAlias, KafkaCatalog.BOOTSTRAP_SERVERS);
        if (property == null
                || property.isNull(0))
        {
            return null;
        }
        String bootstrapServers = property.valueAsString(0);

        int size = getSize();
        for (int i = 0; i < size; i++)
        {
            KafkaConnection connection = getElementAt(i);
            if (CI.equals(connection.getBootstrapServers(), bootstrapServers))
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

    boolean prepare(KafkaConnection connection, boolean silent)
    {
        if (connection == null)
        {
            return false;
        }

        if (connection.hasCredentials())
        {
            return true;
        }

        if (!connection.isSaslEnabled())
        {
            return true;
        }

        if (isBlank(connection.getSaslJaasPassword()))
        {
            if (silent)
            {
                return false;
            }

            Credentials credentials = getCredentials(connection.toString(), connection.getSaslJaasUsername(), false);
            if (credentials == null)
            {
                return false;
            }

            if (!isBlank(credentials.getUsername()))
            {
                connection.setSaslJaasUsername(credentials.getUsername());
            }
            connection.setRuntimeSaslJaasPassword(credentials.getPassword());
            return true;
        }

        if (silent
                && !cryptoService.isInitalized())
        {
            return false;
        }

        String decrypted = cryptoService.decryptString(connection.getSaslJaasPassword());
        if (decrypted == null)
        {
            return false;
        }
        connection.setRuntimeSaslJaasPassword(decrypted.toCharArray());
        return true;
    }

    List<String> getTopics(KafkaConnection connection, boolean forceReload, boolean reThrowError)
    {
        SwingUtilities.invokeLater(() -> reloadButtons.forEach(b -> b.setEnabled(false)));
        try
        {
            synchronized (connection)
            {
                List<String> topics = connection.getTopics();
                if ((!forceReload
                        && topics != null)
                        || (!forceReload
                                && !connection.hasCredentials()))
                {
                    return topics != null ? topics
                            : emptyList();
                }

                try (AdminClient admin = AdminClient.create(connection.toAdminClientProperties()))
                {
                    Set<String> names = admin.listTopics(new ListTopicsOptions().listInternal(false))
                            .names()
                            .get(15, TimeUnit.SECONDS);
                    List<String> result = new ArrayList<>(names);
                    result.sort(String.CASE_INSENSITIVE_ORDER);
                    connection.setTopics(result);
                }
                catch (Exception e)
                {
                    connection.setTopics(emptyList());
                    connection.setRuntimeSaslJaasPassword(null);

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

                    if (reThrowError)
                    {
                        throw new RuntimeException(e);
                    }
                }
                return connection.getTopics();
            }
        }
        finally
        {
            SwingUtilities.invokeLater(() -> reloadButtons.forEach(b -> b.setEnabled(true)));
        }
    }
}
