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

    /**
     * @param resumeSessionId Session ID to resume, or {@code null} to start a new session.
     * @param sessionIdConsumer Called with the session ID returned by the provider after the response completes.
     */
    public AIChatSession(String resumeSessionId, Consumer<String> sessionIdConsumer)
    {
        this.sessionIdConsumer = sessionIdConsumer;
        this.resumeSessionId = resumeSessionId;
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
}
