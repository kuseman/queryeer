package com.queryeer.assistant;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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

/**
 * AI assistant provider that delegates to the locally installed Claude Code CLI ({@code claude}). Uses whatever credentials the CLI is already authenticated with – no API key required. Supports
 * session resumption via {@code claude --resume} so each query file maintains its own persistent conversation.
 */
@Inject
class ClaudeCodeAIAssistantProvider implements IAIAssistantProvider
{
    static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CONFIG_NAME = "ai.assistant.claudecode";
    private static final String KEY_EXECUTABLE = "executable";
    private static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    private static final String DEFAULT_EXECUTABLE = "claude";

    private final IConfig config;
    private final List<Consumer<Boolean>> dirtyStateConsumers = new ArrayList<>();

    private ClaudeCodeSettings settings;
    private PropertiesComponent component;
    private volatile Process currentProcess;
    private volatile ClaudeCodeInfo cachedInfo;
    private final AtomicBoolean infoCollectionScheduled = new AtomicBoolean(false);

    ClaudeCodeAIAssistantProvider(IConfig config, ICryptoService cryptoService)
    {
        this.config = requireNonNull(config, "config");
        this.settings = loadSettings();
    }

    @Override
    public String name()
    {
        return "Claude Code (local)";
    }

    @Override
    public boolean isConfigured()
    {
        boolean found = findExecutable() != null;
        if (found)
        {
            scheduleInfoCollection();
        }
        return found;
    }

    /** Session-aware entry point. Delegates to {@link #doChat}. */
    @Override
    public void chat(List<AIChatMessage> history, String userMessage, String systemPrompt, AIChatSession session, Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError)
    {
        doChat(history, userMessage, systemPrompt, session, onChunk, onComplete, onError);
    }

    /** Non-session entry point for providers that call the old overload directly. */
    @Override
    public void chat(List<AIChatMessage> history, String userMessage, String systemPrompt, Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError)
    {
        doChat(history, userMessage, systemPrompt, null, onChunk, onComplete, onError);
    }

    // CSOFF
    private void doChat(List<AIChatMessage> history, String userMessage, String systemPrompt, AIChatSession session, Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError)
    {
        cancelled = false;

        String executable = findExecutable();
        if (executable == null)
        {
            onError.accept(new IllegalStateException("Claude Code CLI not found. Install it and ensure 'claude' is on your PATH (or set a custom path in Options)."));
            return;
        }

        String resumeId = session != null ? session.getResumeSessionId()
                : null;
        List<String> command = buildCommand(executable, userMessage, systemPrompt, resumeId);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process;
        try
        {
            process = pb.start();
        }
        catch (IOException e)
        {
            onError.accept(new RuntimeException("Failed to start Claude Code process: " + e.getMessage(), e));
            return;
        }
        currentProcess = process;

        // When resuming, Claude Code already holds the history server-side – don't re-send it.
        // On a fresh session, pipe prior history as context then close stdin.
        if (isBlank(resumeId)
                && !history.isEmpty())
        {
            try (OutputStream stdin = process.getOutputStream())
            {
                stdin.write(formatHistory(history).getBytes(StandardCharsets.UTF_8));
            }
            catch (IOException e)
            {
                // Non-fatal: process may have already closed stdin
            }
        }
        else
        {
            try
            {
                process.getOutputStream()
                        .close();
            }
            catch (IOException e)
            {
                // ignore
            }
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (cancelled)
                {
                    break;
                }
                parseStreamLine(line, onChunk, onError, session);
            }
        }
        catch (IOException e)
        {
            if (!cancelled)
            {
                onError.accept(e);
                return;
            }
        }
        finally
        {
            currentProcess = null;
        }
        onComplete.run();
    }
    // CSON

    private volatile boolean cancelled;

    @Override
    public void cancel()
    {
        cancelled = true;
        Process p = currentProcess;
        currentProcess = null;
        if (p != null)
        {
            p.destroyForcibly();
        }
    }

    @Override
    public Component getComponent()
    {
        if (component == null)
        {
            component = new PropertiesComponent(ClaudeCodeSettingsModel.class, this::notifyDirty);
            component.init(new ClaudeCodeSettingsModel(settings));
        }
        return component;
    }

    @Override
    public Map<String, Object> getInfo()
    {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("Executable", settings.executable);
        ClaudeCodeInfo cliInfo = cachedInfo;
        if (cliInfo != null)
        {
            if (!isBlank(cliInfo.version))
            {
                info.put("Version", cliInfo.version);
            }
            if (!isBlank(cliInfo.account))
            {
                info.put("Account", cliInfo.account);
            }
            if (!isBlank(cliInfo.model))
            {
                info.put("Model", cliInfo.model);
            }
        }
        info.put("System Prompt", settings.systemPrompt);
        return info;
    }

    @Override
    public String getTitle()
    {
        return "Claude Code (local)";
    }

    @Override
    public String getLongTitle()
    {
        return "Claude Code Local CLI Settings";
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
        ClaudeCodeSettingsModel updated = (ClaudeCodeSettingsModel) component.getTarget();
        String newExecutable = isBlank(updated.getExecutable()) ? DEFAULT_EXECUTABLE
                : updated.getExecutable();
        if (!newExecutable.equals(settings.executable))
        {
            // Executable changed – re-collect CLI info on next isConfigured() call
            cachedInfo = null;
            infoCollectionScheduled.set(false);
        }
        settings.executable = newExecutable;
        settings.systemPrompt = updated.getSystemPrompt();

        Map<String, Object> map = new HashMap<>();
        map.put(KEY_EXECUTABLE, settings.executable);
        map.put(KEY_SYSTEM_PROMPT, settings.systemPrompt);
        config.saveExtensionConfig(CONFIG_NAME, map);
        return true;
    }

    @Override
    public void revertChanges()
    {
        if (component != null)
        {
            component.init(new ClaudeCodeSettingsModel(settings));
        }
    }

    @Override
    public EncryptionResult reEncryptSecrets(ICryptoService newCryptoService)
    {
        return IConfigurable.EncryptionResult.NO_CHANGE;
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

    private List<String> buildCommand(String executable, String userMessage, String systemPrompt, String resumeId)
    {
        List<String> command = new ArrayList<>();
        command.add(executable);
        if (!isBlank(resumeId))
        {
            command.add("--resume");
            command.add(resumeId);
        }
        command.add("-p");
        command.add(userMessage);
        command.add("--output-format");
        command.add("stream-json");
        command.add("--verbose");
        command.add("--include-partial-messages");
        if (!isBlank(systemPrompt))
        {
            command.add("--system-prompt");
            command.add(systemPrompt);
        }
        return command;
    }

    private void parseStreamLine(String line, Consumer<String> onChunk, Consumer<Throwable> onError, AIChatSession session)
    {
        if (isBlank(line))
        {
            return;
        }
        try
        {
            JsonNode node = MAPPER.readTree(line);
            String type = node.path("type")
                    .asText();

            if ("stream_event".equals(type))
            {
                JsonNode event = node.path("event");
                if ("content_block_delta".equals(event.path("type")
                        .asText())
                        && "text_delta".equals(event.path("delta")
                                .path("type")
                                .asText()))
                {
                    String text = event.path("delta")
                            .path("text")
                            .asText("");
                    if (!text.isEmpty())
                    {
                        onChunk.accept(text);
                    }
                }
            }
            else if ("result".equals(type))
            {
                if ("error_during_execution".equals(node.path("subtype")
                        .asText()))
                {
                    String error = node.path("error")
                            .asText("Unknown error from Claude Code CLI");
                    onError.accept(new RuntimeException("Claude Code error: " + error));
                }
                if (session != null)
                {
                    String sessionId = node.path("session_id")
                            .asText(null);
                    session.notifySessionId(sessionId);
                }
            }
        }
        catch (Exception e)
        {
            // Skip unparseable lines (e.g. debug output)
        }
    }

    private static String formatHistory(List<AIChatMessage> history)
    {
        StringBuilder sb = new StringBuilder("## Conversation History\n\n");
        for (AIChatMessage msg : history)
        {
            if (msg.role() == AIChatMessage.Role.USER)
            {
                sb.append("User: ")
                        .append(msg.content())
                        .append("\n\n");
            }
            else if (msg.role() == AIChatMessage.Role.ASSISTANT)
            {
                sb.append("Assistant: ")
                        .append(msg.content())
                        .append("\n\n");
            }
        }
        return sb.toString();
    }

    /** Returns the resolved executable path, or {@code null} if not found. */
    private String findExecutable()
    {
        String exe = isBlank(settings.executable) ? DEFAULT_EXECUTABLE
                : settings.executable;
        try
        {
            ProcessBuilder pb = new ProcessBuilder(exe, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
            return exe;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private void scheduleInfoCollection()
    {
        if (infoCollectionScheduled.compareAndSet(false, true))
        {
            Thread t = new Thread(this::collectInfo, "ClaudeCode-InfoCollector");
            t.setDaemon(true);
            t.start();
        }
    }

    private void collectInfo()
    {
        String exe = isBlank(settings.executable) ? DEFAULT_EXECUTABLE
                : settings.executable;

        String version = runAndCapture(exe, "--version");

        // "claude config list --output-format json" returns a JSON object whose keys are
        // config entries, e.g. {"model":"claude-sonnet-4-6","account":{"emailAddress":"..."}}
        String account = null;
        String model = null;
        String configJson = runAndCapture(exe, "config", "list", "--output-format", "json");
        if (!isBlank(configJson))
        {
            try
            {
                JsonNode root = MAPPER.readTree(configJson);
                JsonNode accountNode = root.path("account");
                if (!accountNode.isMissingNode())
                {
                    account = accountNode.path("emailAddress")
                            .asText(null);
                    if (isBlank(account))
                    {
                        account = accountNode.asText(null);
                    }
                }
                model = root.path("model")
                        .asText(null);
            }
            catch (Exception ignored)
            {
            }
        }

        cachedInfo = new ClaudeCodeInfo(version, account, model);
    }

    /** Runs a command and returns its stdout+stderr as a trimmed string, or {@code null} on failure. */
    private static String runAndCapture(String... command)
    {
        try
        {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream()
                    .readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor();
            return isBlank(output) ? null
                    : output;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private record ClaudeCodeInfo(String version, String account, String model)
    {
    }

    private void notifyDirty(boolean dirty)
    {
        dirtyStateConsumers.forEach(c -> c.accept(dirty));
    }

    private ClaudeCodeSettings loadSettings()
    {
        Map<String, Object> map = config.loadExtensionConfig(CONFIG_NAME);
        ClaudeCodeSettings s = new ClaudeCodeSettings();
        s.executable = (String) map.getOrDefault(KEY_EXECUTABLE, DEFAULT_EXECUTABLE);
        s.systemPrompt = (String) map.getOrDefault(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
        return s;
    }

    /** Internal config model. */
    private static class ClaudeCodeSettings
    {
        String executable = DEFAULT_EXECUTABLE;
        String systemPrompt = DEFAULT_SYSTEM_PROMPT;
    }

    /** Settings POJO exposed to {@link PropertiesComponent} for the Options UI. */
    @Properties(
            properties = {
                    @Property(
                            propertyName = "executable",
                            title = "Claude Executable",
                            description = "Path to the claude CLI executable. Leave blank to use 'claude' from PATH.",
                            order = 0),
                    @Property(
                            propertyName = "systemPrompt",
                            title = "System Prompt",
                            description = "System prompt sent to Claude. The current query and database schema are appended automatically based on the chat window options.",
                            multiline = true,
                            multilineRows = 5,
                            order = 1) })
    static class ClaudeCodeSettingsModel
    {
        private String executable = DEFAULT_EXECUTABLE;
        private String systemPrompt = DEFAULT_SYSTEM_PROMPT;

        ClaudeCodeSettingsModel()
        {
        }

        ClaudeCodeSettingsModel(ClaudeCodeSettings source)
        {
            this.executable = source.executable;
            this.systemPrompt = source.systemPrompt;
        }

        public String getExecutable()
        {
            return executable;
        }

        public void setExecutable(String executable)
        {
            this.executable = executable;
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
