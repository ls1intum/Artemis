package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import de.tum.cit.aet.artemis.atlas.service.AtlasAgentToolsService;

/**
 * Configuration for Atlas Agent tools integration with Spring AI.
 * This class registers the @Tool-annotated methods from AtlasAgentToolsService
 * so that Spring AI can discover and use them for function calling.
 */
@Configuration
@Conditional(AtlasEnabled.class)
public class AtlasAgentToolConfig {

    /**
     * Registers the tools found on the AtlasAgentToolsService bean.
     * MethodToolCallbackProvider discovers @Tool-annotated methods on the provided instances
     * and makes them available for Spring AI's tool calling system.
     *
     * @param toolsService the service containing @Tool-annotated methods
     * @return ToolCallbackProvider that exposes the tools to Spring AI
     */
    @Bean
    public ToolCallbackProvider atlasToolCallbackProvider(AtlasAgentToolsService toolsService) {
        // MethodToolCallbackProvider discovers @Tool-annotated methods on the provided instances
        return MethodToolCallbackProvider.builder().toolObjects(toolsService).build();
    }

    /**
     * In-memory chat memory for temporary session-based conversation storage.
     * This stores chat history in memory (not database) and is used to maintain
     * conversation context when the user closes and reopens the chat popup.
     * The memory is temporary and will be cleared when the server restarts.
     * Keeps the last 20 messages per conversation by default.
     *
     * @return ChatMemory instance for temporary conversation storage
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(20).build();
    }
}
