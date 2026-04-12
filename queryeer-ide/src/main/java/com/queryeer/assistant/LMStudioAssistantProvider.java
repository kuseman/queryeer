package com.queryeer.assistant;

import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.mcp.IMcpToolService;

/** AI assistant provider backed by a locally running LM Studio server (OpenAI-compatible API). */
@Inject
class LMStudioAssistantProvider extends AOpenAIAssistantProvider
{
    LMStudioAssistantProvider(IConfig config, ICryptoService cryptoService, IMcpToolService mcpToolService)
    {
        super(config, cryptoService, mcpToolService, "ai.assistant.lmstudio", "http://localhost:1234", "local-model");
    }

    @Override
    public String name()
    {
        return "LM Studio";
    }

    @Override
    public String getTitle()
    {
        return "LM Studio";
    }

    @Override
    public String getLongTitle()
    {
        return "LM Studio AI Assistant Settings";
    }
}
