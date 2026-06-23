package de.tum.cit.aet.artemis.atlas.service;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Factory for {@link ToolCallbackProvider} instances. Providers are intentionally NOT Spring beans
 * to avoid Spring AI's {@code ToolCallbackResolver} auto-discovering them and creating a circular
 * dependency back to {@link AtlasAgentDelegationService}.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentToolCallbackService {

    private final ToolCallbackProvider expertProvider;

    private final ToolCallbackProvider mapperProvider;

    private final ToolCallbackProvider exerciseMapperProvider;

    private final ToolCallbackProvider orchestratorProvider;

    private volatile ToolCallbackProvider mainAgentProvider;

    public AtlasAgentToolCallbackService(CompetencyExpertToolsService expertToolsService, CompetencyMappingToolsService mapperToolsService,
            ExerciseMappingToolsService exerciseMapperToolsService, OrchestratorToolsService orchestratorToolsService) {
        this.expertProvider = MethodToolCallbackProvider.builder().toolObjects(expertToolsService).build();
        this.mapperProvider = MethodToolCallbackProvider.builder().toolObjects(mapperToolsService).build();
        this.exerciseMapperProvider = MethodToolCallbackProvider.builder().toolObjects(exerciseMapperToolsService).build();
        this.orchestratorProvider = MethodToolCallbackProvider.builder().toolObjects(orchestratorToolsService).build();
    }

    /**
     * Provider for the Main Agent tools; cached after first call. Tools service is passed in to avoid a circular bean dependency.
     *
     * @param toolsService the main agent tools service
     * @return the provider
     */
    public ToolCallbackProvider createMainAgentProvider(AtlasAgentToolsService toolsService) {
        if (mainAgentProvider == null) {
            mainAgentProvider = MethodToolCallbackProvider.builder().toolObjects(toolsService).build();
        }
        return mainAgentProvider;
    }

    /** @return provider for the Competency Expert sub-agent. */
    public ToolCallbackProvider createCompetencyExpertProvider() {
        return expertProvider;
    }

    /** @return provider for the Competency Mapper sub-agent. */
    public ToolCallbackProvider createCompetencyMapperProvider() {
        return mapperProvider;
    }

    /** @return provider for the Exercise Mapper sub-agent. */
    public ToolCallbackProvider createExerciseMapperProvider() {
        return exerciseMapperProvider;
    }

    /** @return provider for the Competency Orchestrator. */
    public ToolCallbackProvider createOrchestratorProvider() {
        return orchestratorProvider;
    }
}
