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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryeer.api.component.Properties;
import com.queryeer.api.component.PropertiesComponent;
import com.queryeer.api.component.Property;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.Inject;
import com.queryeer.api.extensions.assistant.AIChatMessage;
import com.queryeer.api.extensions.assistant.AIChatSession;
import com.queryeer.api.extensions.assistant.IAIAssistantProvider;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.ICryptoService;

/** AI assistant provider backed by the Anthropic Claude API. */
@Inject
class AnthropicAIAssistantProvider implements IAIAssistantProvider
{
    static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CONFIG_NAME = "ai.assistant.anthropic";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String KEY_ENCRYPTED_API_KEY = "encryptedApiKey";
    private static final String KEY_MODEL = "model";
    private static final String KEY_MAX_TOKENS = "maxTokens";
    private static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";

    private final IConfig config;
    private final ICryptoService cryptoService;
    private final HttpClient httpClient;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();

    private AnthropicSettings settings;
    private PropertiesComponent component;
    private volatile boolean cancelled;
    private volatile CompletableFuture<HttpResponse<InputStream>> pendingRequest;
    private volatile InputStream activeResponseBody;

    AnthropicAIAssistantProvider(IConfig config, ICryptoService cryptoService)
    {
        this.config = requireNonNull(config, "config");
        this.cryptoService = requireNonNull(cryptoService, "cryptoService");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.settings = loadSettings();
    }

    @Override
    public String name()
    {
        return "Anthropic Claude";
    }

    @Override
    public boolean isConfigured()
    {
        return !isBlank(settings.encryptedApiKey);
    }

    // CSOFF
    @Override
    public void chat(List<AIChatMessage> history, String userMessage, String systemPrompt, AIChatSession session, Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError)
    {
        cancelled = false;

        String apiKey = cryptoService.decryptString(settings.encryptedApiKey);
        if (isBlank(apiKey))
        {
            onError.accept(new IllegalStateException("Failed to decrypt API key. Please re-enter it in Options."));
            return;
        }

        String requestBody;
        try
        {
            requestBody = buildRequestBody(history, userMessage, systemPrompt);
        }
        catch (JsonProcessingException e)
        {
            onError.accept(e);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
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
            return;
        }
        catch (ExecutionException e)
        {
            if (!cancelled)
            {
                Throwable cause = e.getCause() != null ? e.getCause()
                        : e;
                onError.accept(cause);
            }
            else
            {
                onComplete.run();
            }
            return;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread()
                    .interrupt();
            onComplete.run();
            return;
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
            return;
        }

        activeResponseBody = response.body();
        try
        {
            streamResponse(response.body(), onChunk, onComplete, onError);
        }
        finally
        {
            activeResponseBody = null;
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
            component = new PropertiesComponent(AnthropicSettingsModel.class, this::notifyDirty);
            component.init(new AnthropicSettingsModel(settings, cryptoService));
        }
        return component;
    }

    @Override
    public Map<String, Object> getInfo()
    {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("Model", settings.model);
        info.put("Max Tokens", settings.maxTokens);
        info.put("System Prompt", settings.systemPrompt);
        return info;
    }

    @Override
    public String getTitle()
    {
        return "Anthropic Claude";
    }

    @Override
    public String getLongTitle()
    {
        return "Anthropic Claude AI Assistant Settings";
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
        AnthropicSettingsModel updated = (AnthropicSettingsModel) component.getTarget();
        String rawApiKey = updated.getApiKey();
        String encryptedApiKey = settings.encryptedApiKey;
        if (!isBlank(rawApiKey))
        {
            encryptedApiKey = cryptoService.encryptString(rawApiKey);
            if (encryptedApiKey == null)
            {
                return false;
            }
        }

        settings.encryptedApiKey = encryptedApiKey;
        settings.model = updated.getModel();
        settings.maxTokens = updated.getMaxTokens();
        settings.systemPrompt = updated.getSystemPrompt();

        Map<String, Object> map = new HashMap<>();
        map.put(KEY_ENCRYPTED_API_KEY, settings.encryptedApiKey);
        map.put(KEY_MODEL, settings.model);
        map.put(KEY_MAX_TOKENS, settings.maxTokens);
        map.put(KEY_SYSTEM_PROMPT, settings.systemPrompt);
        config.saveExtensionConfig(CONFIG_NAME, map);
        return true;
    }

    @Override
    public void revertChanges()
    {
        if (component != null)
        {
            component.init(new AnthropicSettingsModel(settings, cryptoService));
        }
    }

    @Override
    public EncryptionResult reEncryptSecrets(ICryptoService newCryptoService)
    {
        if (isBlank(settings.encryptedApiKey))
        {
            return IConfigurable.EncryptionResult.NO_CHANGE;
        }
        String decrypted = cryptoService.decryptString(settings.encryptedApiKey);
        if (decrypted == null)
        {
            return IConfigurable.EncryptionResult.ABORT;
        }
        String reEncrypted = newCryptoService.encryptString(decrypted);
        if (reEncrypted == null)
        {
            return IConfigurable.EncryptionResult.ABORT;
        }
        settings.encryptedApiKey = reEncrypted;
        return IConfigurable.EncryptionResult.SUCCESS;
    }

    private void streamResponse(InputStream body, Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError)
    {
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
                    String type = node.path("type")
                            .asText();
                    if ("content_block_delta".equals(type))
                    {
                        String text = node.path("delta")
                                .path("text")
                                .asText("");
                        if (!text.isEmpty())
                        {
                            onChunk.accept(text);
                        }
                    }
                    else if ("error".equals(type))
                    {
                        String message = node.path("error")
                                .path("message")
                                .asText("Unknown API error");
                        onError.accept(new RuntimeException("Anthropic API error: " + message));
                        return;
                    }
                }
                catch (Exception e)
                {
                    // Skip unparseable SSE data lines
                }
            }
            onComplete.run();
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
        }
    }

    private String buildRequestBody(List<AIChatMessage> history, String userMessage, String systemPrompt) throws JsonProcessingException
    {
        List<Map<String, String>> messages = new ArrayList<>();
        for (AIChatMessage msg : history)
        {
            if (msg.role() == AIChatMessage.Role.USER
                    || msg.role() == AIChatMessage.Role.ASSISTANT)
            {
                Map<String, String> m = new HashMap<>();
                m.put("role", msg.role()
                        .name()
                        .toLowerCase());
                m.put("content", msg.content());
                messages.add(m);
            }
        }
        Map<String, String> currentMsg = new HashMap<>();
        currentMsg.put("role", "user");
        currentMsg.put("content", userMessage);
        messages.add(currentMsg);

        Map<String, Object> body = new HashMap<>();
        body.put("model", settings.model);
        body.put("max_tokens", settings.maxTokens);
        body.put("stream", true);
        if (!isBlank(systemPrompt))
        {
            body.put("system", systemPrompt);
        }
        body.put("messages", messages);
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
            return new RuntimeException("Anthropic API error " + statusCode + ": " + message);
        }
        catch (Exception e)
        {
            return new RuntimeException("Anthropic API error " + statusCode + ": " + responseBody);
        }
    }

    private void notifyDirty(boolean dirty)
    {
        dirtyStateConsumers.forEach(c -> c.accept(dirty));
    }

    private AnthropicSettings loadSettings()
    {
        Map<String, Object> map = config.loadExtensionConfig(CONFIG_NAME);
        AnthropicSettings s = new AnthropicSettings();
        s.encryptedApiKey = (String) map.getOrDefault(KEY_ENCRYPTED_API_KEY, null);
        s.model = (String) map.getOrDefault(KEY_MODEL, DEFAULT_MODEL);
        Object maxTokensVal = map.get(KEY_MAX_TOKENS);
        s.maxTokens = maxTokensVal instanceof Number n ? n.intValue()
                : DEFAULT_MAX_TOKENS;
        s.systemPrompt = (String) map.getOrDefault(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
        return s;
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

    /** Internal config model. */
    private static class AnthropicSettings
    {
        String encryptedApiKey;
        String model = DEFAULT_MODEL;
        int maxTokens = DEFAULT_MAX_TOKENS;
        String systemPrompt = DEFAULT_SYSTEM_PROMPT;
    }

    /** Settings POJO exposed to {@link PropertiesComponent} for the Options UI. */
    @Properties(
            properties = {
                    @Property(
                            propertyName = "apiKey",
                            title = "API Key",
                            description = "Anthropic API key. Leave blank to keep the existing key.",
                            password = true,
                            order = 0),
                    @Property(
                            propertyName = "model",
                            title = "Model",
                            description = "Claude model to use (e.g. claude-sonnet-4-6, claude-opus-4-6, claude-haiku-4-5-20251001)",
                            order = 1),
                    @Property(
                            propertyName = "maxTokens",
                            title = "Max Tokens",
                            description = "Maximum number of tokens in the response",
                            order = 2),
                    @Property(
                            propertyName = "systemPrompt",
                            title = "System Prompt",
                            description = "Base system prompt sent to Claude. The current query and database schema are appended automatically based on the chat window options.",
                            multiline = true,
                            multilineRows = 5,
                            order = 3) })
    static class AnthropicSettingsModel
    {
        private String apiKey = "";
        private String model = DEFAULT_MODEL;
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private String systemPrompt = DEFAULT_SYSTEM_PROMPT;

        AnthropicSettingsModel()
        {
        }

        AnthropicSettingsModel(AnthropicSettings source, ICryptoService cryptoService)
        {
            // Don't decrypt the key into the UI by default – user re-enters it to change it
            this.apiKey = "";
            this.model = source.model;
            this.maxTokens = source.maxTokens;
            this.systemPrompt = source.systemPrompt;
        }

        public String getApiKey()
        {
            return apiKey;
        }

        public void setApiKey(String apiKey)
        {
            this.apiKey = apiKey;
        }

        public String getModel()
        {
            return model;
        }

        public void setModel(String model)
        {
            this.model = model;
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
    }
}
