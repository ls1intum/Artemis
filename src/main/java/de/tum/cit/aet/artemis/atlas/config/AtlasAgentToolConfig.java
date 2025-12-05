package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.atlas.service.AtlasAgentToolsService;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService;
import de.tum.cit.aet.artemis.atlas.service.CompetencyMappingToolsService;

@Lazy
@Configuration
@Conditional(AtlasEnabled.class)
public class AtlasAgentToolConfig {

    /**
     * Registers the tools for the Main Atlas Agent (Requirements Engineer/Orchestrator).
     * This agent has access to information retrieval tools only.
     *
     * @param toolsService the service containing @Tool-annotated methods for main agent
     * @return ToolCallbackProvider that exposes the main agent tools to Spring AI
     */
    @Bean
    @Lazy
    @Qualifier("mainAgentToolCallbackProvider")
    public ToolCallbackProvider mainAgentToolCallbackProvider(AtlasAgentToolsService toolsService) {
        return MethodToolCallbackProvider.builder().toolObjects(toolsService).build();
    }

    /**
     * Registers the tools for the Competency Expert sub-agent.
     * This agent has access to both previewCompetency and createCompetency tools.
     *
     * @param expertToolsService the service containing @Tool-annotated methods for competency expert
     * @return ToolCallbackProvider that exposes the competency expert tools to Spring AI
     */
    @Bean
    @Lazy
    @Qualifier("competencyExpertToolCallbackProvider")
    public ToolCallbackProvider competencyExpertToolCallbackProvider(CompetencyExpertToolsService expertToolsService) {
        return MethodToolCallbackProvider.builder().toolObjects(expertToolsService).build();
    }

    /**
     * Registers the tools for the Competency Mapper sub-agent.
     * This agent has access to relation mapping and preview tools.
     *
     * @param mapperToolsService the service containing @Tool-annotated methods for competency mapper
     * @return ToolCallbackProvider that exposes the competency mapper tools to Spring AI
     */
    @Bean
    @Lazy
    @Qualifier("competencyMapperToolCallbackProvider")
    public ToolCallbackProvider competencyMapperToolCallbackProvider(CompetencyMappingToolsService mapperToolsService) {
        return MethodToolCallbackProvider.builder().toolObjects(mapperToolsService).build();
    }
}
