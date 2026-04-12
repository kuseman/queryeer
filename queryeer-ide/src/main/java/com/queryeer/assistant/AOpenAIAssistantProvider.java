package com.queryeer.assistant;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.queryeer.api.component.Properties;
import com.queryeer.api.component.PropertiesComponent;
import com.queryeer.api.component.Property;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.assistant.AIChatMessage;
import com.queryeer.api.extensions.assistant.AIChatSession;
import com.queryeer.api.extensions.assistant.IAIAssistantProvider;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.mcp.IMcpToolService;
import com.queryeer.mcp.McpTool;
import com.queryeer.mcp.McpToolParameter;

/**
 * Abstract base for OpenAI-compatible local AI assistant providers (LM Studio, Ollama). Subclasses supply provider identity and defaults; this class owns all HTTP/SSE protocol logic.
 */
abstract class AOpenAIAssistantProvider implements IAIAssistantProvider
{
    static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String KEY_BASE_URL = "baseUrl";
    private static final String KEY_MODEL = "model";
    private static final String KEY_MAX_TOKENS = "maxTokens";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    private static final String KEY_ENCRYPTED_API_TOKEN = "encryptedApiToken";

    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int MAX_TOOL_ITERATIONS = 10;

    private final IConfig config;
    private final ICryptoService cryptoService;
    private final IMcpToolService mcpToolService;
    private final String configName;
    private final String defaultBaseUrl;
    private final String defaultModel;
    private final HttpClient httpClient;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();

    private LocalAISettings settings;
    private PropertiesComponent component;
    private volatile boolean cancelled;
    private volatile CompletableFuture<HttpResponse<InputStream>> pendingRequest;
    private volatile InputStream activeResponseBody;

    AOpenAIAssistantProvider(IConfig config, ICryptoService cryptoService, IMcpToolService mcpToolService, String configName, String defaultBaseUrl, String defaultModel)
    {
        this.config = requireNonNull(config, "config");
        this.cryptoService = requireNonNull(cryptoService, "cryptoService");
        this.mcpToolService = requireNonNull(mcpToolService, "mcpToolService");
        this.configName = requireNonNull(configName, "configName");
        this.defaultBaseUrl = requireNonNull(defaultBaseUrl, "defaultBaseUrl");
        this.defaultModel = requireNonNull(defaultModel, "defaultModel");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.settings = loadSettings();
    }

    @Override
    public boolean isConfigured()
    {
        return !isBlank(settings.baseUrl);
    }

    @Override
    public void chat(List<AIChatMessage> history, String userMessage, String systemPrompt, AIChatSession session, Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError)
    {
        doChatLoop(history, userMessage, systemPrompt, session, onChunk, onComplete, onError);
    }

    // CSOFF
    private void doChatLoop(List<AIChatMessage> history, String userMessage, String systemPrompt, AIChatSession session, Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError)
    {
        cancelled = false;

        List<Map<String, Object>> messages = buildMessages(history, userMessage, systemPrompt);
        List<Map<String, Object>> tools = fetchMcpTools();

        for (int iter = 0; iter < MAX_TOOL_ITERATIONS; iter++)
        {
            if (cancelled)
            {
                onComplete.run();
                return;
            }

            String requestBody;
            try
            {
                requestBody = buildRequestBody(messages, tools);
            }
            catch (JsonProcessingException e)
            {
                onError.accept(e);
                return;
            }

            HttpResponse<InputStream> response = sendHttpRequest(requestBody, onComplete, onError);
            if (response == null)
            {
                // sendHttpRequest already called onComplete or onError
                return;
            }

            activeResponseBody = response.body();
            List<ToolCall> toolCalls;
            try
            {
                toolCalls = streamResponse(response.body(), onChunk);
            }
            catch (IOException e)
            {
                if (!cancelled)
                {
                    onError.accept(e);
                }
                else
                {
                    onComplete.run();
                }
                return;
            }
            finally
            {
                activeResponseBody = null;
            }

            if (toolCalls.isEmpty()
                    || cancelled)
            {
                onComplete.run();
                return;
            }

            // Append the assistant's tool-call message then the results and loop
            appendToolCallMessages(messages, toolCalls, session);
        }

        if (!cancelled)
        {
            onComplete.run();
        }
    }
    // CSON

    @Override
    public void cancel()
    {
        cancelled = true;
        CompletableFuture<HttpResponse<InputStream>> pending = pendingRequest;
        if (pending != null)
        {
            pending.cancel(true);
        }
        InputStream body = activeResponseBody;
        if (body != null)
        {
            try
            {
                body.close();
            }
            catch (IOException e)
            {
                // Ignore – closing intentionally to interrupt the read loop
            }
        }
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new PropertiesComponent(LocalAISettingsModel.class, this::notifyDirty);
            component.init(new LocalAISettingsModel(settings));
        }
        return component;
    }

    @Override
    public Map<String, Object> getInfo()
    {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("Base URL", settings.baseUrl);
        info.put("Model", settings.model);
        info.put("Temperature", settings.temperature);
        info.put("Max Tokens", settings.maxTokens);
        return info;
    }

    @Override
    public String groupName()
    {
        return "AI Assistant";
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

    @Override
    public boolean commitChanges()
    {
        if (component == null)
        {
            return false;
        }
        LocalAISettingsModel updated = (LocalAISettingsModel) component.getTarget();
        settings.baseUrl = updated.getBaseUrl();
        settings.model = updated.getModel();
        settings.maxTokens = updated.getMaxTokens();
        settings.temperature = parseTemperature(updated.getTemperature());
        settings.systemPrompt = updated.getSystemPrompt();

        String rawToken = updated.getApiToken();
        if (!isBlank(rawToken))
        {
            String encrypted = cryptoService.encryptString(rawToken);
            if (encrypted == null)
            {
                return false;
            }
            settings.encryptedApiToken = encrypted;
        }

        Map<String, Object> map = new HashMap<>();
        map.put(KEY_BASE_URL, settings.baseUrl);
        map.put(KEY_MODEL, settings.model);
        map.put(KEY_MAX_TOKENS, settings.maxTokens);
        map.put(KEY_TEMPERATURE, settings.temperature);
        map.put(KEY_SYSTEM_PROMPT, settings.systemPrompt);
        map.put(KEY_ENCRYPTED_API_TOKEN, settings.encryptedApiToken);
        config.saveExtensionConfig(configName, map);
        return true;
    }

    @Override
    public void revertChanges()
    {
        if (component != null)
        {
            component.init(new LocalAISettingsModel(settings));
        }
    }

    @Override
    public EncryptionResult reEncryptSecrets(ICryptoService newCryptoService)
    {
        if (isBlank(settings.encryptedApiToken))
        {
            return IConfigurable.EncryptionResult.NO_CHANGE;
        }
        String decrypted = cryptoService.decryptString(settings.encryptedApiToken);
        if (decrypted == null)
        {
            return IConfigurable.EncryptionResult.ABORT;
        }
        String reEncrypted = newCryptoService.encryptString(decrypted);
        if (reEncrypted == null)
        {
            return IConfigurable.EncryptionResult.ABORT;
        }
        settings.encryptedApiToken = reEncrypted;
        return IConfigurable.EncryptionResult.SUCCESS;
    }

    @Override
    public String getSystemPrompt()
    {
        return settings.systemPrompt;
    }

    @Override
    public ResponseFormat getResponseFormat()
    {
        return ResponseFormat.MARKDOWN;
    }

    // ---- HTTP helpers ----

    /** Sends the streaming chat-completions request. Returns null if the call was cancelled or an error was reported via onError. */
    private HttpResponse<InputStream> sendHttpRequest(String requestBody, Runnable onComplete, Consumer<Throwable> onError)
    {
        String url = settings.baseUrl.replaceAll("/+$", "") + "/v1/chat/completions";
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("content-type", "application/json")
                .header("accept", "text/event-stream");
        if (!isBlank(settings.encryptedApiToken))
        {
            String token = cryptoService.decryptString(settings.encryptedApiToken);
            if (!isBlank(token))
            {
                requestBuilder.header("Authorization", "Bearer " + token);
            }
        }
        HttpRequest request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        pendingRequest = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        HttpResponse<InputStream> response;
        try
        {
            response = pendingRequest.get();
            pendingRequest = null;
        }
        catch (CancellationException e)
        {
            onComplete.run();
            return null;
        }
        catch (ExecutionException e)
        {
            if (!cancelled)
            {
                Throwable cause = e.getCause() != null ? e.getCause()
                        : e;
                onError.accept(new RuntimeException("Could not connect to " + name() + " at " + settings.baseUrl + ". Is the server running?", cause));
            }
            else
            {
                onComplete.run();
            }
            return null;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread()
                    .interrupt();
            onComplete.run();
            return null;
        }

        if (response.statusCode() != 200)
        {
            String errorBody;
            try
            {
                errorBody = new String(response.body()
                        .readAllBytes(), StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                errorBody = "(could not read error response)";
            }
            onError.accept(parseApiError(response.statusCode(), errorBody));
            return null;
        }

        return response;
    }

    /**
     * Reads the SSE stream, forwarding text chunks to {@code onChunk} and accumulating any tool-call deltas.
     *
     * @return list of tool calls the model wants to invoke, or an empty list when the response is text-only
     */
    private List<ToolCall> streamResponse(InputStream body, Consumer<String> onChunk) throws IOException
    {
        Map<Integer, ToolCallBuilder> pending = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null
                    && !cancelled)
            {
                if (!line.startsWith("data: "))
                {
                    continue;
                }
                String data = line.substring(6);
                if ("[DONE]".equals(data))
                {
                    break;
                }
                try
                {
                    JsonNode node = MAPPER.readTree(data);
                    JsonNode choice = node.path("choices")
                            .path(0);
                    JsonNode delta = choice.path("delta");

                    // Text content
                    JsonNode contentNode = delta.path("content");
                    if (!contentNode.isMissingNode()
                            && !contentNode.isNull())
                    {
                        String text = contentNode.asText("");
                        if (!text.isEmpty())
                        {
                            onChunk.accept(text);
                        }
                    }

                    // Tool-call deltas
                    JsonNode toolCallsNode = delta.path("tool_calls");
                    if (toolCallsNode.isArray())
                    {
                        for (JsonNode tc : toolCallsNode)
                        {
                            int idx = tc.path("index")
                                    .asInt(0);
                            ToolCallBuilder builder = pending.computeIfAbsent(idx, i -> new ToolCallBuilder());
                            if (tc.has("id"))
                            {
                                builder.id = tc.path("id")
                                        .asText();
                            }
                            JsonNode fn = tc.path("function");
                            if (fn.has("name"))
                            {
                                builder.name = fn.path("name")
                                        .asText();
                            }
                            if (fn.has("arguments"))
                            {
                                builder.arguments.append(fn.path("arguments")
                                        .asText());
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    // Skip unparseable SSE data lines
                }
            }
        }

        if (pending.isEmpty())
        {
            return List.of();
        }
        return pending.values()
                .stream()
                .map(b -> new ToolCall(b.id, b.name, b.arguments.toString()))
                .collect(Collectors.toList());
    }

    // ---- MCP integration ----

    /** Converts active MCP tools to OpenAI {@code tools} request format. Returns an empty list if no tools are configured. */
    private List<Map<String, Object>> fetchMcpTools()
    {
        return mcpToolService.getTools()
                .stream()
                .map(this::toOpenAiTool)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toOpenAiTool(McpTool tool)
    {
        ObjectNode propertiesNode = MAPPER.createObjectNode();
        List<String> required = new ArrayList<>();
        for (McpToolParameter param : tool.getParameters())
        {
            ObjectNode prop = MAPPER.createObjectNode();
            prop.put("type", param.getType()
                    .getJsonSchemaType());
            prop.put("description", param.getDescription());
            propertiesNode.set(param.getName(), prop);
            required.add(param.getName());
        }
        ObjectNode parametersSchema = MAPPER.createObjectNode();
        parametersSchema.put("type", "object");
        parametersSchema.set("properties", propertiesNode);
        parametersSchema.set("required", MAPPER.valueToTree(required));

        Map<String, Object> func = new LinkedHashMap<>();
        func.put("name", tool.getName());
        func.put("description", tool.getDescription());
        func.put("parameters", MAPPER.convertValue(parametersSchema, Map.class));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "function");
        entry.put("function", func);
        return entry;
    }

    /** Calls a single MCP tool directly (no HTTP). Never throws; returns an error string on failure. */
    @SuppressWarnings("unchecked")
    private String callMcpTool(String toolName, String argumentsJson)
    {
        try
        {
            Map<String, Object> args = MAPPER.readValue(argumentsJson, Map.class);
            return mcpToolService.executeTool(toolName, args);
        }
        catch (Exception e)
        {
            return "Error calling tool '" + toolName + "': " + e.getMessage();
        }
    }

    /**
     * Appends an assistant message containing the tool_calls and then a tool-result message for each call to the running messages list.
     */
    private void appendToolCallMessages(List<Map<String, Object>> messages, List<ToolCall> toolCalls, AIChatSession session)
    {
        // Assistant message with the requested tool calls
        List<Map<String, Object>> toolCallObjs = new ArrayList<>();
        for (ToolCall tc : toolCalls)
        {
            Map<String, Object> fnObj = new LinkedHashMap<>();
            fnObj.put("name", tc.name());
            fnObj.put("arguments", tc.arguments());

            Map<String, Object> tcObj = new LinkedHashMap<>();
            tcObj.put("id", tc.id());
            tcObj.put("type", "function");
            tcObj.put("function", fnObj);
            toolCallObjs.add(tcObj);
        }

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", null);
        assistantMsg.put("tool_calls", toolCallObjs);
        messages.add(assistantMsg);

        // One tool-result message per call
        for (ToolCall tc : toolCalls)
        {
            if (session != null)
            {
                session.notifyToolUse(tc.name() + "(" + tc.arguments() + ")");
            }
            String result = callMcpTool(tc.name(), tc.arguments());
            Map<String, Object> toolResultMsg = new LinkedHashMap<>();
            toolResultMsg.put("role", "tool");
            toolResultMsg.put("tool_call_id", tc.id());
            toolResultMsg.put("content", result);
            messages.add(toolResultMsg);
        }
    }

    // ---- Request building ----

    /** Builds the initial messages list from history + current user message. Returned list is mutable for the tool loop. */
    private List<Map<String, Object>> buildMessages(List<AIChatMessage> history, String userMessage, String systemPrompt)
    {
        List<Map<String, Object>> messages = new ArrayList<>();

        // OpenAI format uses a system role message (unlike Anthropic's top-level "system" field)
        if (!isBlank(systemPrompt))
        {
            Map<String, Object> sysMsg = new LinkedHashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }

        for (AIChatMessage msg : history)
        {
            if (msg.role() == AIChatMessage.Role.USER
                    || msg.role() == AIChatMessage.Role.ASSISTANT)
            {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", msg.role()
                        .name()
                        .toLowerCase());
                m.put("content", msg.content());
                messages.add(m);
            }
        }

        Map<String, Object> currentMsg = new LinkedHashMap<>();
        currentMsg.put("role", "user");
        currentMsg.put("content", userMessage);
        messages.add(currentMsg);

        return messages;
    }

    /** Serialises the full request body including the tools array (omitted when empty). */
    private String buildRequestBody(List<Map<String, Object>> messages, List<Map<String, Object>> tools) throws JsonProcessingException
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model);
        body.put("max_tokens", settings.maxTokens);
        body.put("temperature", settings.temperature);
        body.put("stream", true);
        body.put("messages", messages);
        if (!tools.isEmpty())
        {
            body.put("tools", tools);
        }
        return MAPPER.writeValueAsString(body);
    }

    private RuntimeException parseApiError(int statusCode, String responseBody)
    {
        try
        {
            JsonNode node = MAPPER.readTree(responseBody);
            String message = node.path("error")
                    .path("message")
                    .asText(responseBody);
            return new RuntimeException(name() + " API error " + statusCode + ": " + message);
        }
        catch (Exception e)
        {
            return new RuntimeException(name() + " API error " + statusCode + ": " + responseBody);
        }
    }

    private static double parseTemperature(String value)
    {
        if (isBlank(value))
        {
            return DEFAULT_TEMPERATURE;
        }
        try
        {
            double d = Double.parseDouble(value.trim());
            return (d >= 0.0
                    && d <= 2.0) ? d
                            : DEFAULT_TEMPERATURE;
        }
        catch (NumberFormatException e)
        {
            return DEFAULT_TEMPERATURE;
        }
    }

    private void notifyDirty(boolean dirty)
    {
        dirtyStateConsumers.forEach(c -> c.accept(dirty));
    }

    private LocalAISettings loadSettings()
    {
        Map<String, Object> map = config.loadExtensionConfig(configName);
        LocalAISettings s = new LocalAISettings();
        s.baseUrl = (String) map.getOrDefault(KEY_BASE_URL, defaultBaseUrl);
        s.model = (String) map.getOrDefault(KEY_MODEL, defaultModel);
        Object maxTokensVal = map.get(KEY_MAX_TOKENS);
        s.maxTokens = maxTokensVal instanceof Number n ? n.intValue()
                : DEFAULT_MAX_TOKENS;
        Object tempVal = map.get(KEY_TEMPERATURE);
        s.temperature = tempVal instanceof Number n ? n.doubleValue()
                : DEFAULT_TEMPERATURE;
        s.systemPrompt = (String) map.getOrDefault(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
        s.encryptedApiToken = (String) map.getOrDefault(KEY_ENCRYPTED_API_TOKEN, null);
        return s;
    }

    // ---- Inner types ----

    /** Accumulated data for one tool call during streaming. */
    private static class ToolCallBuilder
    {
        String id;
        String name;
        StringBuilder arguments = new StringBuilder();
    }

    /** Completed tool-call request parsed from a streaming response. */
    private record ToolCall(String id, String name, String arguments)
    {
    }

    /** Internal config state. */
    private static class LocalAISettings
    {
        String baseUrl;
        String model;
        int maxTokens = DEFAULT_MAX_TOKENS;
        double temperature = DEFAULT_TEMPERATURE;
        String systemPrompt = DEFAULT_SYSTEM_PROMPT;
        String encryptedApiToken;
    }

    /** Settings POJO exposed to {@link PropertiesComponent} for the Options UI. */
    @Properties(
            properties = {
                    @Property(
                            propertyName = "baseUrl",
                            title = "Base URL",
                            description = "Base URL of the local AI server (e.g. http://localhost:1234 for LM Studio, http://localhost:11434 for Ollama)",
                            order = 0),
                    @Property(
                            propertyName = "model",
                            title = "Model",
                            description = "Model name to use. Must match an available/loaded model on the server.",
                            order = 1),
                    @Property(
                            propertyName = "temperature",
                            title = "Temperature",
                            description = "Sampling temperature (0.0–2.0). Lower values are more deterministic.",
                            order = 2),
                    @Property(
                            propertyName = "maxTokens",
                            title = "Max Tokens",
                            description = "Maximum number of tokens to generate in the response.",
                            order = 3),
                    @Property(
                            propertyName = "systemPrompt",
                            title = "System Prompt",
                            description = "Base system prompt. The current query and database schema are appended automatically based on the chat window options.",
                            multiline = true,
                            multilineRows = 5,
                            order = 4),
                    @Property(
                            propertyName = "apiToken",
                            title = "Bearer Token",
                            description = "Optional Bearer token sent as 'Authorization: Bearer <token>'. Leave blank to connect without authentication.",
                            password = true,
                            order = 5) })
    static class LocalAISettingsModel
    {
        private String baseUrl;
        private String model;
        private String temperature = String.valueOf(DEFAULT_TEMPERATURE);
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private String systemPrompt = DEFAULT_SYSTEM_PROMPT;
        private String apiToken = "";

        LocalAISettingsModel()
        {
        }

        LocalAISettingsModel(LocalAISettings source)
        {
            this.baseUrl = source.baseUrl;
            this.model = source.model;
            this.temperature = String.valueOf(source.temperature);
            this.maxTokens = source.maxTokens;
            this.systemPrompt = source.systemPrompt;
            // Don't pre-fill the token field – user re-enters it to change it
            this.apiToken = "";
        }

        public String getBaseUrl()
        {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl)
        {
            this.baseUrl = baseUrl;
        }

        public String getModel()
        {
            return model;
        }

        public void setModel(String model)
        {
            this.model = model;
        }

        public String getTemperature()
        {
            return temperature;
        }

        public void setTemperature(String temperature)
        {
            this.temperature = temperature;
        }

        public int getMaxTokens()
        {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens)
        {
            this.maxTokens = maxTokens;
        }

        public String getSystemPrompt()
        {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt)
        {
            this.systemPrompt = systemPrompt;
        }

        public String getApiToken()
        {
            return apiToken;
        }

        public void setApiToken(String apiToken)
        {
            this.apiToken = apiToken;
        }
    }
}
