package se.kuseman.payloadbuilder.catalog.kafka;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.IPayloadbuilderState;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.catalog.Column.Type;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.test.VectorTestUtils;

/** Test of {@link KafkaCatalogExtension}. */
class KafkaCatalogExtensionTest
{
    @Test
    void test_handle_exception_non_credentials()
    {
        IQueryFileProvider queryFileProvider = Mockito.mock(IQueryFileProvider.class);
        ICryptoService cryptoService = Mockito.mock(ICryptoService.class);
        IQuerySession session = Mockito.mock(IQuerySession.class);

        KafkaConnectionsModel model = new KafkaConnectionsModel(queryFileProvider, cryptoService);
        KafkaCatalogExtension extension = new KafkaCatalogExtension(queryFileProvider, model, "kafka");

        assertEquals(ICatalogExtension.ExceptionAction.NONE, extension.handleException(session, new CatalogException("kafka", "boom")));
    }

    @Test
    void test_setup() throws InvocationTargetException, InterruptedException
    {
        IQueryFileProvider queryFileProvider = Mockito.mock(IQueryFileProvider.class);
        IQuerySession session = Mockito.mock(IQuerySession.class);
        IQueryFile queryFile = Mockito.mock(IQueryFile.class);
        ICryptoService cryptoService = Mockito.mock(ICryptoService.class);
        IPayloadbuilderState state = Mockito.mock(IPayloadbuilderState.class);
        when(state.getQuerySession()).thenReturn(session);

        when(queryFileProvider.getCurrentFile()).thenReturn(queryFile);
        when(queryFile.getEngineState()).thenReturn(state);

        KafkaConnectionsModel model = new KafkaConnectionsModel(queryFileProvider, cryptoService)
        {
            @Override
            List<String> getTopics(KafkaConnection connection, boolean forceReload, boolean reThrowError)
            {
                return connection.getTopics();
            }
        };

        KafkaCatalogExtension extension = new KafkaCatalogExtension(queryFileProvider, model, "kafka");

        KafkaConnection connection = new KafkaConnection();
        connection.setName("dev");
        connection.setBootstrapServers("localhost:9092");
        connection.setSchemaRegistryUrl("http://localhost:8081");
        connection.setSecurityProtocol(KafkaConnection.SecurityProtocol.SASL_SSL);
        connection.setSaslMechanism(KafkaConnection.SaslMechanism.SCRAM_SHA_512);
        connection.setSaslJaasLoginModule("org.apache.kafka.common.security.scram.ScramLoginModule");
        connection.setSaslJaasControlFlag("required");
        connection.setSaslJaasUsername("user");
        connection.setRuntimeSaslJaasPassword("password".toCharArray());
        connection.setTopics(asList("topic_a", "topic_b"));
        model.setConnections(asList(connection));

        extension.setupConnection(connection);

        Mockito.verify(session)
                .setCatalogProperty("kafka", KafkaCatalog.BOOTSTRAP_SERVERS, "localhost:9092");
        Mockito.verify(session)
                .setCatalogProperty("kafka", KafkaCatalog.SCHEMA_REGISTRY_URL, "http://localhost:8081");
        Mockito.verify(session)
                .setCatalogProperty("kafka", KafkaCatalog.SECURITY_PROTOCOL, KafkaConnection.SecurityProtocol.SASL_SSL.name());
        Mockito.verify(session)
                .setCatalogProperty("kafka", KafkaCatalog.SASL_MECHANISM, KafkaConnection.SaslMechanism.SCRAM_SHA_512.name());

        extension.setupTopic("topic_b");
        Mockito.verify(session)
                .setCatalogProperty("kafka", KafkaCatalog.TOPIC, "topic_b");
    }

    @Test
    void test_update() throws InvocationTargetException, InterruptedException
    {
        IQueryFileProvider queryFileProvider = Mockito.mock(IQueryFileProvider.class);
        IQuerySession session = Mockito.mock(IQuerySession.class);
        IQueryFile queryFile = Mockito.mock(IQueryFile.class);
        ICryptoService cryptoService = Mockito.mock(ICryptoService.class);
        IPayloadbuilderState state = Mockito.mock(IPayloadbuilderState.class);
        when(state.getQuerySession()).thenReturn(session);
        when(queryFileProvider.getCurrentFile()).thenReturn(queryFile);
        when(queryFile.getEngineState()).thenReturn(state);

        KafkaConnectionsModel model = new KafkaConnectionsModel(queryFileProvider, cryptoService)
        {
            @Override
            List<String> getTopics(KafkaConnection connection, boolean forceReload, boolean reThrowError)
            {
                return connection.getTopics();
            }
        };

        KafkaCatalogExtension extension = new KafkaCatalogExtension(queryFileProvider, model, "kafka");

        KafkaConnection connection1 = new KafkaConnection();
        connection1.setBootstrapServers("localhost:9092");
        connection1.setTopics(asList("orders", "payments"));
        KafkaConnection connection2 = new KafkaConnection();
        connection2.setBootstrapServers("localhost:9093");
        connection2.setTopics(asList("metrics"));
        model.setConnections(asList(connection1, connection2));

        KafkaCatalogExtension.QuickPropertiesPanel qp = (KafkaCatalogExtension.QuickPropertiesPanel) extension.getQuickPropertiesComponent();

        doReturn(VectorTestUtils.vv(Type.String, "localhost:9092")).when(session)
                .getCatalogProperty("kafka", KafkaCatalog.BOOTSTRAP_SERVERS);
        doReturn(VectorTestUtils.vv(Type.String, "payments")).when(session)
                .getCatalogProperty("kafka", KafkaCatalog.TOPIC);

        SwingUtilities.invokeAndWait(() -> extension.update(queryFile));
        SwingUtilities.invokeAndWait(() ->
        {
            // Flush queued UI updates from invokeLater in extension.update
        });

        assertEquals("localhost:9092", ((KafkaConnection) qp.connections.getSelectedItem()).getBootstrapServers());
        assertEquals("payments", qp.topics.getSelectedItem());
    }

    @Test
    void test_update_sets_default_connection_in_session_when_missing() throws InvocationTargetException, InterruptedException
    {
        IQueryFileProvider queryFileProvider = Mockito.mock(IQueryFileProvider.class);
        IQuerySession session = Mockito.mock(IQuerySession.class);
        IQueryFile queryFile = Mockito.mock(IQueryFile.class);
        ICryptoService cryptoService = Mockito.mock(ICryptoService.class);
        IPayloadbuilderState state = Mockito.mock(IPayloadbuilderState.class);
        when(state.getQuerySession()).thenReturn(session);
        when(queryFileProvider.getCurrentFile()).thenReturn(queryFile);
        when(queryFile.getEngineState()).thenReturn(state);

        KafkaConnectionsModel model = new KafkaConnectionsModel(queryFileProvider, cryptoService)
        {
            @Override
            List<String> getTopics(KafkaConnection connection, boolean forceReload, boolean reThrowError)
            {
                return connection.getTopics();
            }
        };

        KafkaCatalogExtension extension = new KafkaCatalogExtension(queryFileProvider, model, "kafka");

        KafkaConnection connection1 = new KafkaConnection();
        connection1.setBootstrapServers("localhost:9092");
        connection1.setTopics(asList("orders", "payments"));
        model.setConnections(asList(connection1));

        KafkaCatalogExtension.QuickPropertiesPanel qp = (KafkaCatalogExtension.QuickPropertiesPanel) extension.getQuickPropertiesComponent();

        doReturn(null).when(session)
                .getCatalogProperty("kafka", KafkaCatalog.BOOTSTRAP_SERVERS);
        doReturn(VectorTestUtils.vv(Type.String, "payments")).when(session)
                .getCatalogProperty("kafka", KafkaCatalog.TOPIC);

        SwingUtilities.invokeAndWait(() -> extension.update(queryFile));
        SwingUtilities.invokeAndWait(() ->
        {
            // Flush queued UI updates from invokeLater in extension.update
        });

        Mockito.verify(session)
                .setCatalogProperty("kafka", KafkaCatalog.BOOTSTRAP_SERVERS, "localhost:9092");
        assertEquals("localhost:9092", ((KafkaConnection) qp.connections.getSelectedItem()).getBootstrapServers());
    }
}
