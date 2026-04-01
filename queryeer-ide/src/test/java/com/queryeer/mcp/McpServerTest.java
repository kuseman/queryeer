package com.queryeer.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Integration-style tests for {@link McpServer} */
class McpServerTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TEST_PORT = 19371; // unlikely to be in use

    private McpServer server;

    @BeforeEach
    void setup()
    {
        McpServerConfig config = new McpServerConfig();
        config.setPort(TEST_PORT);
        McpHttpHandler handler = new McpHttpHandler(new McpHttpHandlerTest.MockConfig(List.of()), config, new McpHttpHandlerTest.MockTemplateService());
        server = new McpServer(handler);
    }

    @AfterEach
    void teardown()
    {
        server.stop();
    }

    // ---- Lifecycle tests ----

    @Test
    void start_sets_running_true() throws IOException
    {
        assertFalse(server.isRunning());
        startServer();
        assertTrue(server.isRunning());
    }

    @Test
    void stop_sets_running_false() throws IOException
    {
        startServer();
        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void stop_when_not_running_is_noop()
    {
        assertFalse(server.isRunning());
        server.stop(); // should not throw
        assertFalse(server.isRunning());
    }

    @Test
    void restart_works_on_running_server() throws IOException
    {
        startServer();
        McpServerConfig config = new McpServerConfig();
        config.setPort(TEST_PORT);
        server.restart(config);
        assertTrue(server.isRunning());
    }

    @Test
    void port_in_use_throws_on_start() throws IOException
    {
        startServer();
        McpServerConfig config2 = new McpServerConfig();
        config2.setPort(TEST_PORT);
        McpHttpHandler handler2 = new McpHttpHandler(new McpHttpHandlerTest.MockConfig(List.of()), config2, new McpHttpHandlerTest.MockTemplateService());
        McpServer server2 = new McpServer(handler2);
        try
        {
            assertThrows(IOException.class, () -> server2.start(config2));
        }
        finally
        {
            server2.stop();
        }
    }

    @Test
    void started_server_handles_initialize_request() throws IOException
    {
        startServer();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        JsonNode node = postJson("/mcp", body, 200);
        assertEquals(1, node.path("id")
                .asInt());
        assertTrue(node.path("result")
                .has("capabilities"));
    }

    // ---- OAuth endpoint tests ----

    @Test
    void as_metadata_returns_all_required_endpoints() throws IOException
    {
        startServer();
        JsonNode meta = getJson("/.well-known/oauth-authorization-server");
        assertEquals("http://127.0.0.1:" + TEST_PORT, meta.path("issuer")
                .asText());
        assertTrue(meta.path("authorization_endpoint")
                .asText()
                .contains("/authorize"));
        assertTrue(meta.path("token_endpoint")
                .asText()
                .contains("/token"));
        assertTrue(meta.path("registration_endpoint")
                .asText()
                .contains("/register"));
        assertTrue(meta.path("code_challenge_methods_supported")
                .toString()
                .contains("S256"));
        assertTrue(meta.path("token_endpoint_auth_methods_supported")
                .toString()
                .contains("none"));
    }

    @Test
    void protected_resource_metadata_references_mcp_endpoint() throws IOException
    {
        startServer();
        JsonNode meta = getJson("/.well-known/oauth-protected-resource");
        assertTrue(meta.path("resource")
                .asText()
                .contains("/mcp"));
        assertTrue(meta.path("authorization_servers")
                .isArray());
    }

    @Test
    void register_echoes_redirect_uris_and_adds_client_id() throws IOException
    {
        startServer();
        String body = "{\"redirect_uris\":[\"http://127.0.0.1:9999/callback\"],\"grant_types\":[\"authorization_code\"]}";
        JsonNode response = postJson("/register", body, 201);
        assertEquals("queryeer-mcp", response.path("client_id")
                .asText());
        assertTrue(response.path("redirect_uris")
                .isArray());
        assertEquals("http://127.0.0.1:9999/callback", response.path("redirect_uris")
                .get(0)
                .asText());
        assertEquals(0, response.path("client_secret_expires_at")
                .asInt());
    }

    @Test
    void authorize_redirects_to_redirect_uri_with_code_and_state() throws IOException
    {
        startServer();
        URL url = new URL("http://127.0.0.1:" + TEST_PORT
                          + "/authorize?response_type=code&client_id=queryeer-mcp"
                          + "&redirect_uri=http%3A%2F%2F127.0.0.1%3A9999%2Fcallback"
                          + "&code_challenge=abc&code_challenge_method=S256&state=mystate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        String location = conn.getHeaderField("Location");
        assertEquals(302, conn.getResponseCode());
        assertTrue(location.startsWith("http://127.0.0.1:9999/callback"));
        assertTrue(location.contains("code="));
        assertTrue(location.contains("state=mystate"));
    }

    @Test
    void authorize_without_state_omits_state_param() throws IOException
    {
        startServer();
        URL url = new URL("http://127.0.0.1:" + TEST_PORT
                          + "/authorize?response_type=code&client_id=queryeer-mcp"
                          + "&redirect_uri=http%3A%2F%2F127.0.0.1%3A9999%2Fcallback"
                          + "&code_challenge=abc&code_challenge_method=S256");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        String location = conn.getHeaderField("Location");
        assertTrue(location.contains("code="));
        assertFalse(location.contains("state="));
    }

    @Test
    void token_returns_bearer_token() throws IOException
    {
        startServer();
        String body = "grant_type=authorization_code&code=queryeer-local" + "&redirect_uri=http%3A%2F%2F127.0.0.1%3A9999%2Fcallback&client_id=queryeer-mcp";
        JsonNode response = postForm("/token", body, 200);
        assertEquals("queryeer-local", response.path("access_token")
                .asText());
        assertEquals("Bearer", response.path("token_type")
                .asText());
        assertTrue(response.path("expires_in")
                .asInt() > 0);
    }

    @Test
    void unknown_path_returns_json_404() throws IOException
    {
        startServer();
        URL url = new URL("http://127.0.0.1:" + TEST_PORT + "/nonexistent");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        assertEquals(404, conn.getResponseCode());
        assertEquals("application/json", conn.getHeaderField("Content-Type"));
        JsonNode body = MAPPER.readTree(conn.getErrorStream()
                .readAllBytes());
        assertEquals("not_found", body.path("error")
                .asText());
    }

    // ---- Helpers ----

    private void startServer() throws IOException
    {
        McpServerConfig config = new McpServerConfig();
        config.setPort(TEST_PORT);
        server.start(config);
    }

    private JsonNode getJson(String path) throws IOException
    {
        URL url = new URL("http://127.0.0.1:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        return MAPPER.readTree(conn.getInputStream()
                .readAllBytes());
    }

    private JsonNode postJson(String path, String body, int expectedStatus) throws IOException
    {
        URL url = new URL("http://127.0.0.1:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream())
        {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(expectedStatus, conn.getResponseCode());
        return MAPPER.readTree(conn.getInputStream()
                .readAllBytes());
    }

    private JsonNode postForm(String path, String body, int expectedStatus) throws IOException
    {
        URL url = new URL("http://127.0.0.1:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream os = conn.getOutputStream())
        {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(expectedStatus, conn.getResponseCode());
        return MAPPER.readTree(conn.getInputStream()
                .readAllBytes());
    }
}
