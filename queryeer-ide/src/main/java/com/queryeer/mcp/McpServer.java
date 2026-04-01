package com.queryeer.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Manages the lifecycle of the embedded JDK HttpServer for MCP. Binds to 127.0.0.1 (loopback only) to prevent remote access.
 *
 * <p>
 * Also implements a minimal rubber-stamp OAuth 2.1 server so that MCP clients that enforce OAuth (e.g. Claude Code) can complete the authorization flow. All requests are accepted without real
 * authentication — this is intentionally safe because the server is bound to loopback only.
 * </p>
 */
class McpServer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(McpServer.class);

    private final McpHttpHandler handler;
    private HttpServer httpServer;
    private ExecutorService executor;

    McpServer(McpHttpHandler handler)
    {
        this.handler = handler;
    }

    /**
     * Starts the server on the port specified in config. Binds to 127.0.0.1 only.
     *
     * @throws IOException if the port is already in use or the server cannot start
     */
    synchronized void start(McpServerConfig config) throws IOException
    {
        stop();
        int port = config.getPort();
        handler.updateConfig(config);
        executor = Executors.newCachedThreadPool(r ->
        {
            Thread t = new Thread(r, "mcp-handler");
            t.setDaemon(true);
            return t;
        });
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        // MCP endpoint
        httpServer.createContext("/mcp", handler);

        // OAuth 2.1 AS metadata (RFC 8414)
        httpServer.createContext("/.well-known/oauth-authorization-server", e -> handleAsMetadata(e, port));

        // OAuth 2.0 Protected Resource Metadata (RFC 9728)
        httpServer.createContext("/.well-known/oauth-protected-resource", e -> handleProtectedResource(e, port));

        // Dynamic Client Registration (RFC 7591) — rubber-stamp any client
        httpServer.createContext("/register", McpServer::handleRegister);

        // Authorization endpoint — immediately redirect back with code (no real auth)
        httpServer.createContext("/authorize", McpServer::handleAuthorize);

        // Token endpoint — issue a static local access token
        httpServer.createContext("/token", McpServer::handleToken);

        // Catch-all for any other paths
        httpServer.createContext("/", e -> sendJson(e, 404, "{\"error\":\"not_found\"}"));

        httpServer.setExecutor(executor);
        httpServer.start();
        LOGGER.info("MCP server started on 127.0.0.1:{}", port);
    }

    /** Stops the server gracefully. No-op if not running. */
    synchronized void stop()
    {
        if (httpServer != null)
        {
            try
            {
                httpServer.stop(1);
            }
            catch (Exception e)
            {
                LOGGER.warn("Error stopping MCP server", e);
            }
            httpServer = null;
        }
        if (executor != null)
        {
            executor.shutdown();
            try
            {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread()
                        .interrupt();
            }
            executor = null;
        }
    }

    /** Stops and restarts with updated config. */
    synchronized void restart(McpServerConfig config) throws IOException
    {
        stop();
        start(config);
    }

    /** Returns true if the server is currently running. */
    synchronized boolean isRunning()
    {
        return httpServer != null;
    }

    // ---- OAuth handler implementations ----

    private static void handleAsMetadata(HttpExchange exchange, int port) throws IOException
    {
        String base = "http://127.0.0.1:" + port;
        // @formatter:off
        String json = "{"
                + "\"issuer\":\"" + base + "\","
                + "\"authorization_endpoint\":\"" + base + "/authorize\","
                + "\"token_endpoint\":\"" + base + "/token\","
                + "\"registration_endpoint\":\"" + base + "/register\","
                + "\"response_types_supported\":[\"code\"],"
                + "\"grant_types_supported\":[\"authorization_code\"],"
                + "\"code_challenge_methods_supported\":[\"S256\"],"
                + "\"token_endpoint_auth_methods_supported\":[\"none\"]"
                + "}";
        // @formatter:on
        sendJson(exchange, 200, json);
    }

    private static void handleProtectedResource(HttpExchange exchange, int port) throws IOException
    {
        String base = "http://127.0.0.1:" + port;
        String json = "{\"resource\":\"" + base + "/mcp\",\"authorization_servers\":[\"" + base + "\"]}";
        sendJson(exchange, 200, json);
    }

    @SuppressWarnings("unchecked")
    private static void handleRegister(HttpExchange exchange) throws IOException
    {
        // RFC 7591: echo back the submitted client metadata plus a server-assigned client_id
        Map<String, Object> body;
        try
        {
            body = McpConfigurable.MAPPER.readValue(exchange.getRequestBody(), Map.class);
        }
        catch (Exception e)
        {
            body = new LinkedHashMap<>();
        }
        body.put("client_id", "queryeer-mcp");
        body.put("client_secret_expires_at", 0);
        sendJson(exchange, 201, McpConfigurable.MAPPER.writeValueAsString(body));
    }

    private static void handleAuthorize(HttpExchange exchange) throws IOException
    {
        // Rubber-stamp: redirect immediately back to redirect_uri with a static code
        Map<String, String> params = parseQuery(exchange.getRequestURI()
                .getQuery());
        String redirectUri = params.getOrDefault("redirect_uri", "");
        String state = params.getOrDefault("state", "");
        String sep = redirectUri.contains("?") ? "&"
                : "?";
        String location = redirectUri + sep
                          + "code=queryeer-local"
                          + (state.isEmpty() ? ""
                                  : "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8));
        exchange.getResponseHeaders()
                .set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void handleToken(HttpExchange exchange) throws IOException
    {
        // Issue a static bearer token regardless of the code presented
        String json = "{\"access_token\":\"queryeer-local\",\"token_type\":\"Bearer\",\"expires_in\":86400}";
        sendJson(exchange, 200, json);
    }

    // ---- Utilities ----

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException
    {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders()
                .set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(body);
        }
    }

    private static Map<String, String> parseQuery(String query)
    {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null)
        {
            return params;
        }
        for (String pair : query.split("&"))
        {
            int idx = pair.indexOf('=');
            if (idx > 0)
            {
                try
                {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                }
                catch (Exception e)
                {
                    // skip malformed pair
                }
            }
        }
        return params;
    }
}
