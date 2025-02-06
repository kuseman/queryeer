package se.kuseman.payloadbuilder.catalog.es;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.ReturnsArgumentAt;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.IPayloadbuilderState;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.utils.CredentialUtils.Credentials;

import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.api.catalog.Column.Type;
import se.kuseman.payloadbuilder.api.execution.IQuerySession;
import se.kuseman.payloadbuilder.catalog.CredentialsException;
import se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Connection;
import se.kuseman.payloadbuilder.test.VectorTestUtils;

/** Test of {@link ESCatalogExtension} */
public class ESCatalogExtensionTest extends Assert
{
    @Test
    public void test_handle_exception()
    {
        IQueryFileProvider queryFileProvider = Mockito.mock(IQueryFileProvider.class);
        IQuerySession session = Mockito.mock(IQuerySession.class);
        IIconFactory iconFactory = Mockito.mock(IIconFactory.class);
        ICryptoService cryptoService = Mockito.mock(ICryptoService.class);
        when(cryptoService.decryptString(anyString())).thenAnswer(new ReturnsArgumentAt(0));

        MutableObject<String> username = new MutableObject<>();
        MutableObject<char[]> password = new MutableObject<>();

        ESConnectionsModel model = new ESConnectionsModel(queryFileProvider, cryptoService)
        {
            @Override
            protected Credentials getCredentials(String connectionDescription, String prefilledUsername, boolean readOnlyUsername)
            {
                return new Credentials(username.getValue(), password.getValue());
            }
        };
        ESCompletionProvider completionProvider = new ESCompletionProvider(model);

        ESCatalogExtension extension = new ESCatalogExtension(queryFileProvider, model, completionProvider, "es", iconFactory);
        extension.getQuickPropertiesComponent();

        // Not a credentials exception
        assertEquals(ICatalogExtension.ExceptionAction.NONE, extension.handleException(session, new CatalogException("es", "fail")));

        // Test with a connection that don't exist in model
        when(session.getCatalogProperty("es", ESCatalog.ENDPOINT_KEY)).thenReturn(VectorTestUtils.vv(Type.String, "http://elastic"));
        when(session.getCatalogProperty("es", ESCatalog.AUTH_USERNAME_KEY)).thenReturn(VectorTestUtils.vv(Type.String, (String) null));

        username.setValue("username");
        password.setValue(new char[] { 'p', 'a', 's', 's' });

        assertEquals(ICatalogExtension.ExceptionAction.RERUN, extension.handleException(session, new CredentialsException("es", "wrong password")));

        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_USERNAME_KEY, username.getValue());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_PASSWORD_KEY, password.getValue());

        Mockito.reset(session);

        ESConnectionsModel.Connection connection = connection("http://endpoint");
        connection.setAuthType(AuthType.BASIC);
        connection.setAuthUsername("prefilled_username");

        // Test with selected connection that has no credentials
        model.setConnections(asList(connection));

        when(session.getCatalogProperty("es", ESCatalog.ENDPOINT_KEY)).thenReturn(VectorTestUtils.vv(Type.String, "http://endpoint"));
        when(session.getCatalogProperty("es", ESCatalog.AUTH_USERNAME_KEY)).thenReturn(VectorTestUtils.vv(Type.String, (String) null));
        username.setValue("username1");
        password.setValue(new char[] { 'p', 'a', 's', 's', '1' });

        assertEquals(ICatalogExtension.ExceptionAction.RERUN, extension.handleException(session, new CredentialsException("es", "wrong password")));

        // Assert connection and session has got updated values
        assertArrayEquals(password.getValue(), connection.getRuntimeAuthPassword());
        // The username of the connection should not be overwritten
        assertEquals("prefilled_username", connection.getAuthUsername());

        Mockito.verify(session)
                .getCatalogProperty("es", ESCatalog.ENDPOINT_KEY);
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.ENDPOINT_KEY, connection.getEndpoint());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.TRUSTCERTIFICATE_KEY, connection.isTrustCertificate());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.CONNECT_TIMEOUT_KEY, connection.getConnectTimeout());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.RECEIVE_TIMEOUT_KEY, connection.getReceiveTimeout());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_TYPE_KEY, connection.getAuthType()
                        .toString());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_USERNAME_KEY, "prefilled_username");
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_PASSWORD_KEY, password.getValue());

        Mockito.reset(session);

        // Test with same username as connection
        when(session.getCatalogProperty("es", ESCatalog.ENDPOINT_KEY)).thenReturn(VectorTestUtils.vv(Type.String, "http://endpoint"));
        username.setValue(connection.getAuthUsername());

        assertEquals(ICatalogExtension.ExceptionAction.RERUN, extension.handleException(session, new CredentialsException("es", "wrong password")));

        // Assert connection and session has got updated values
        assertArrayEquals(password.getValue(), connection.getRuntimeAuthPassword());
        // The username of the connection should not be overwritten
        assertEquals("prefilled_username", connection.getAuthUsername());

        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_USERNAME_KEY, username.getValue());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_PASSWORD_KEY, password.getValue());

        Mockito.reset(session);

        // Test that connection credentials are set to session for a connection that is not the selected one
        // but matches the session endpoint

        // Session contains "http://endpoint" which is not the selected one

        ESConnectionsModel.Connection newConnection = connection("http://endpoint2");
        model.setConnections(asList(connection, newConnection));
        ESCatalogExtension.QuickPropertiesPanel qp = (ESCatalogExtension.QuickPropertiesPanel) extension.getQuickPropertiesComponent();
        qp.connections.setSelectedItem(newConnection);

        when(session.getCatalogProperty("es", ESCatalog.ENDPOINT_KEY)).thenReturn(VectorTestUtils.vv(Type.String, "http://endpoint"));

        assertEquals(ICatalogExtension.ExceptionAction.RERUN, extension.handleException(session, new CredentialsException("es", "wrong password")));

        // Verify that extension switched to the credentials not selected
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_USERNAME_KEY, connection.getAuthUsername());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_PASSWORD_KEY, connection.getRuntimeAuthPassword());

        Mockito.reset(session);

        // Test with selected connection that has encrypted credentials
        connection = connection("http://endpoint");
        connection.setAuthType(AuthType.BASIC);
        connection.setAuthUsername("prefilled_username");
        connection.setAuthPassword("encrypted");
        model.setConnections(asList(connection));

        when(session.getCatalogProperty("es", ESCatalog.ENDPOINT_KEY)).thenReturn(VectorTestUtils.vv(Type.String, "http://endpoint"));
        when(session.getCatalogProperty("es", ESCatalog.AUTH_USERNAME_KEY)).thenReturn(VectorTestUtils.vv(Type.String, (String) null));

        assertEquals(ICatalogExtension.ExceptionAction.RERUN, extension.handleException(session, new CredentialsException("es", "wrong password")));

        // Assert connection and session has got updated values
        assertArrayEquals(connection.getAuthPassword()
                .toCharArray(), connection.getRuntimeAuthPassword());
        // The username of the connection should not be overwritten
        assertEquals("prefilled_username", connection.getAuthUsername());

        Mockito.verify(session)
                .getCatalogProperty("es", ESCatalog.ENDPOINT_KEY);
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.ENDPOINT_KEY, connection.getEndpoint());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.TRUSTCERTIFICATE_KEY, connection.isTrustCertificate());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.CONNECT_TIMEOUT_KEY, connection.getConnectTimeout());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.RECEIVE_TIMEOUT_KEY, connection.getReceiveTimeout());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_TYPE_KEY, connection.getAuthType()
                        .toString());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_USERNAME_KEY, "prefilled_username");
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_PASSWORD_KEY, connection.getAuthPassword()
                        .toCharArray());
    }

    @Test
    public void test_setup()
    {
        IQueryFileProvider queryFileProvider = Mockito.mock(IQueryFileProvider.class);
        IQuerySession session = Mockito.mock(IQuerySession.class);
        IQueryFile queryFile = Mockito.mock(IQueryFile.class);
        IIconFactory iconFactory = Mockito.mock(IIconFactory.class);
        ICryptoService cryptoService = Mockito.mock(ICryptoService.class);
        IPayloadbuilderState state = Mockito.mock(IPayloadbuilderState.class);
        when(state.getQuerySession()).thenReturn(session);

        when(queryFileProvider.getCurrentFile()).thenReturn(queryFile);
        when(queryFile.getEngineState()).thenReturn(state);

        MutableObject<String> username = new MutableObject<>();
        MutableObject<char[]> password = new MutableObject<>();

        ESConnectionsModel model = new ESConnectionsModel(queryFileProvider, cryptoService)
        {
            @Override
            protected Credentials getCredentials(String connectionDescription, String prefilledUsername, boolean readyOnlyUsername)
            {
                return new Credentials(username.getValue(), password.getValue());
            }
        };
        ESCompletionProvider completionProvider = new ESCompletionProvider(model);

        // CSOFF
        ESCatalogExtension extension = new ESCatalogExtension(queryFileProvider, model, completionProvider, "es", iconFactory);

        // CSON

        Connection connection = connection("http://endpoint");
        connection.setAuthType(AuthType.BASIC);
        connection.setAuthUsername("user");
        connection.setAuthPassword("pass");
        connection.setReceiveTimeout(10);
        connection.setConnectTimeout(20);
        connection.setTrustCertificate(true);
        connection.setIndices(asList(new ESConnectionsModel.Index("index", se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Index.Type.ALIAS)));

        model.setConnections(asList(connection));

        extension.setupConnection(connection);

        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.ENDPOINT_KEY, connection.getEndpoint());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_TYPE_KEY, connection.getAuthType()
                        .toString());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.TRUSTCERTIFICATE_KEY, connection.isTrustCertificate());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_USERNAME_KEY, connection.getAuthUsername());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.AUTH_PASSWORD_KEY, connection.getRuntimeAuthPassword());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.CONNECT_TIMEOUT_KEY, connection.getConnectTimeout());
        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.RECEIVE_TIMEOUT_KEY, connection.getReceiveTimeout());

        extension.setupIndex(null);

        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.INDEX_KEY, (String) null);

        extension.setupIndex(connection.getIndices()
                .get(0));

        Mockito.verify(session)
                .setCatalogProperty("es", ESCatalog.INDEX_KEY, connection.getIndices()
                        .get(0).name);
    }

    @Test
    public void test_update() throws InvocationTargetException, InterruptedException
    {
        IQueryFileProvider queryFileProvider = Mockito.mock(IQueryFileProvider.class);
        IQuerySession session = Mockito.mock(IQuerySession.class);
        IQueryFile queryFile = Mockito.mock(IQueryFile.class);
        IIconFactory iconFactory = Mockito.mock(IIconFactory.class);
        ICryptoService cryptoService = Mockito.mock(ICryptoService.class);
        IPayloadbuilderState state = Mockito.mock(IPayloadbuilderState.class);
        when(state.getQuerySession()).thenReturn(session);
        when(queryFileProvider.getCurrentFile()).thenReturn(queryFile);
        when(queryFile.getEngineState()).thenReturn(state);

        MutableObject<String> username = new MutableObject<>();
        MutableObject<char[]> password = new MutableObject<>();

        ESConnectionsModel model = new ESConnectionsModel(queryFileProvider, cryptoService)
        {
            @Override
            protected Credentials getCredentials(String connectionDescription, String prefilledUsername, boolean readOnlyUsername)
            {
                return new Credentials(username.getValue(), password.getValue());
            }
        };
        ESCompletionProvider completionProvider = new ESCompletionProvider(model);

        // CSOFF
        ESCatalogExtension extension = new ESCatalogExtension(queryFileProvider, model, completionProvider, "es", iconFactory);
        // CSON

        // Connection 1 is un-authed
        Connection connection1 = connection("http://endpoint1");
        connection1.setAuthType(AuthType.BASIC);
        connection1.setIndices(asList(new ESConnectionsModel.Index("c1Index1", se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Index.Type.ALIAS),
                new ESConnectionsModel.Index("c1Index2", se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Index.Type.ALIAS)));
        Connection connection2 = connection("http://endpoint2");
        connection2.setIndices(asList(new ESConnectionsModel.Index("c2Index1", se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Index.Type.ALIAS),
                new ESConnectionsModel.Index("c2Index2", se.kuseman.payloadbuilder.catalog.es.ESConnectionsModel.Index.Type.ALIAS)));

        model.setConnections(asList(connection1, connection2));

        ESCatalogExtension.QuickPropertiesPanel qp = (ESCatalogExtension.QuickPropertiesPanel) extension.getQuickPropertiesComponent();
        qp.connections.setSelectedItem(connection2);

        assertEquals("http://endpoint2", ((ESConnectionsModel.Connection) qp.connections.getSelectedItem()).getEndpoint());
        assertNull((qp.indices.getSelectedItem()));
        assertNull(connection1.getRuntimeAuthPassword());

        // Verify that connection is changed according to session values
        when(session.getCatalogProperty("es", ESCatalog.ENDPOINT_KEY)).thenReturn(VectorTestUtils.vv(Type.String, "http://endpoint1"));
        when(session.getCatalogProperty("es", ESCatalog.INDEX_KEY)).thenReturn(VectorTestUtils.vv(Type.String, "c1Index2"));
        when(session.getCatalogProperty("es", ESCatalog.AUTH_PASSWORD_KEY)).thenReturn(VectorTestUtils.vv(Type.Any, "pass".toCharArray()));

        // Run in EDT
        SwingUtilities.invokeAndWait(() -> extension.update(queryFile));

        assertEquals("http://endpoint1", ((ESConnectionsModel.Connection) qp.connections.getSelectedItem()).getEndpoint());
        assertEquals("c1Index2", ((ESConnectionsModel.Index) qp.indices.getSelectedItem()).name);
        // Verify that we pass on the password to the newly selected connection from session password
        assertArrayEquals("pass".toCharArray(), connection1.getRuntimeAuthPassword());

        Mockito.reset(session);
    }

    private ESConnectionsModel.Connection connection(String endpoint)
    {
        ESConnectionsModel.Connection c = new ESConnectionsModel.Connection();
        c.setEndpoint(endpoint);
        return c;
    }
}
