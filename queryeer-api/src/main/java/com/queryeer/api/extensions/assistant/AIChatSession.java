package com.queryeer.api.extensions.assistant;

import java.util.function.Consumer;

/**
 * Carries optional session-resume state into {@link IAIAssistantProvider#chat} and receives the session identifier produced by the response. Providers that support server-side session continuity
 * (e.g. Claude Code CLI via {@code --resume}) should use this to avoid replaying the full conversation history on every turn.
 */
public class AIChatSession
{
    private final String resumeSessionId;
    private final Consumer<String> sessionIdConsumer;
    private final Consumer<String> onToolUse;

    /**
     * @param resumeSessionId Session ID to resume, or {@code null} to start a new session.
     * @param sessionIdConsumer Called with the session ID returned by the provider after the response completes.
     * @param onToolUse Called with a human-readable description each time a tool is invoked. May be {@code null}.
     */
    public AIChatSession(String resumeSessionId, Consumer<String> sessionIdConsumer, Consumer<String> onToolUse)
    {
        this.sessionIdConsumer = sessionIdConsumer;
        this.resumeSessionId = resumeSessionId;
        this.onToolUse = onToolUse;
    }

    /** Returns the session ID to resume, or {@code null} if this is a new session. */
    public String getResumeSessionId()
    {
        return resumeSessionId;
    }

    /** Called by the provider when it has obtained a session ID from the response. */
    public void notifySessionId(String sessionId)
    {
        if (sessionIdConsumer != null
                && sessionId != null
                && !sessionId.isEmpty())
        {
            sessionIdConsumer.accept(sessionId);
        }
    }

    /**
     * Called by the provider just before a tool is executed. {@code description} is a short human-readable label such as {@code "execute_query({\"sql\":\"SELECT 1\"})"}. No-op when no
     * {@code onToolUse} callback was supplied.
     */
    public void notifyToolUse(String description)
    {
        if (onToolUse != null
                && description != null)
        {
            onToolUse.accept(description);
        }
    }
}
