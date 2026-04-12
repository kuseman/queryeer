package com.queryeer.assistant;

import static java.util.Objects.requireNonNull;

import java.util.List;

import com.queryeer.api.extensions.Inject;
import com.queryeer.api.extensions.assistant.IAIAssistantProvider;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.mcp.IMcpToolService;

/** Service that manages the AI assistant chat window and session. */
@Inject
public class AIAssistantService
{
    private final List<IAIAssistantProvider> providers;
    private final IQueryFileProvider queryFileProvider;
    private final IEventBus eventBus;
    private final IMcpToolService mcpToolService;
    private AIChatWindow chatWindow;

    AIAssistantService(List<IAIAssistantProvider> providers, IQueryFileProvider queryFileProvider, IEventBus eventBus, IMcpToolService mcpToolService)
    {
        this.providers = requireNonNull(providers, "providers");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.eventBus = requireNonNull(eventBus, "eventBus");
        this.mcpToolService = requireNonNull(mcpToolService, "mcpToolService");
    }

    /** Show or bring to front the AI assistant chat window. */
    public void showChatWindow()
    {
        if (chatWindow == null)
        {
            chatWindow = new AIChatWindow(providers, queryFileProvider, eventBus, mcpToolService);
        }
        chatWindow.setVisible(true);
        chatWindow.toFront();
    }
}
