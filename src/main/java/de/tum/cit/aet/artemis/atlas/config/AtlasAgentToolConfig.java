package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.atlas.service.AtlasAgentToolsService;

@Lazy
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
    @Lazy
    public ToolCallbackProvider atlasToolCallbackProvider(AtlasAgentToolsService toolsService) {
        return MethodToolCallbackProvider.builder().toolObjects(toolsService).build();
    }
}
