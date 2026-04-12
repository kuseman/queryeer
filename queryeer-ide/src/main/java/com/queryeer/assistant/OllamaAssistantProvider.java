package com.queryeer.assistant;

import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.mcp.IMcpToolService;

/** AI assistant provider backed by a locally running Ollama server (OpenAI-compatible API). */
@Inject
class OllamaAssistantProvider extends AOpenAIAssistantProvider
{
    OllamaAssistantProvider(IConfig config, ICryptoService cryptoService, IMcpToolService mcpToolService)
    {
        super(config, cryptoService, mcpToolService, "ai.assistant.ollama", "http://localhost:11434", "llama3");
    }

    @Override
    public String name()
    {
        return "Ollama";
    }

    @Override
    public String getTitle()
    {
        return "Ollama";
    }

    @Override
    public String getLongTitle()
    {
        return "Ollama AI Assistant Settings";
    }
}
