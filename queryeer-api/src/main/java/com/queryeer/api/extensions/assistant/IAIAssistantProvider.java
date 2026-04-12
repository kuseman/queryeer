package com.queryeer.api.extensions.assistant;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.queryeer.api.extensions.IConfigurable;

/**
 * Extension point for pluggable AI assistant providers. Implementations provide the AI backend (OpenAI, Anthropic, Ollama etc.) and are configurable via the Options dialog.
 */
public interface IAIAssistantProvider extends IConfigurable
{
    static final String DEFAULT_SYSTEM_PROMPT = "You are an AI assistant helping with database queries and SQL.";

    /** Format that the provider's response text is delivered in. */
    enum ResponseFormat
    {
        /** Plain text – displayed as-is. */
        PLAIN_TEXT,
        /** Markdown – rendered to HTML in the chat window. */
        MARKDOWN
    }

    /** Display name of this provider (e.g. "OpenAI GPT-4", "Anthropic Claude"). */
    String name();

    /** Returns true if the provider is configured and ready to use. */
    boolean isConfigured();

    /**
     * Send a chat request to the AI.
     *
     * <p>
     * This method is called in a background thread.
     * </p>
     *
     * @param history Prior chat messages (not including the current user message)
     * @param userMessage The current user message
     * @param systemPrompt System prompt providing context (e.g. current query, database schema)
     * @param onChunk Called for each streaming chunk of the response text
     * @param onComplete Called when the response is fully received
     * @param onError Called if an error occurs during the request
     */
    void chat(List<AIChatMessage> history, String userMessage, String systemPrompt, AIChatSession session, Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError);

    /** Cancel any ongoing chat request. */
    default void cancel()
    {
    }

    /**
     * Return the base system prompt for this provider. The chat window appends context (current query, database schema) to this base prompt before sending to the AI.
     */
    default String getSystemPrompt()
    {
        return DEFAULT_SYSTEM_PROMPT;
    }

    /** Return the format in which this provider delivers response text. Default is {@link ResponseFormat#PLAIN_TEXT}. */
    default ResponseFormat getResponseFormat()
    {
        return ResponseFormat.PLAIN_TEXT;
    }

    /**
     * Return a map of key/value pairs summarising this provider's current settings for display in the AI chat window. Keys are display labels, values are the current setting values. Return an empty
     * map if not supported.
     */
    default Map<String, Object> getInfo()
    {
        return Collections.emptyMap();
    }
}
