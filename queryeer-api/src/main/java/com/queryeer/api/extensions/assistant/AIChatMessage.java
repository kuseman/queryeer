package com.queryeer.api.extensions.assistant;

/** A message in an AI assistant chat session. */
public record AIChatMessage(Role role, String content)
{
    /** Role of the message sender. */
    public enum Role
    {
        USER,
        ASSISTANT,
        SYSTEM
    }
}
