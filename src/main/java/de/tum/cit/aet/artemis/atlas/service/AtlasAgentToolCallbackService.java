package de.tum.cit.aet.artemis.atlas.service;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Factory for creating {@link ToolCallbackProvider} instances on-demand.
 * <p>
 * Providers are NOT registered as Spring beans to avoid being auto-discovered by
 * Spring AI's {@code ToolCallbackResolver}, which would create a circular dependency:
 * {@code AtlasAgentDelegationService → ChatClient → ChatModel → ToolCallingManager
 * → ToolCallbackResolver → [ToolCallbackProvider beans] → AtlasAgentToolsService
 * → AtlasAgentDelegationService}.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentToolCallbackService {

    private final CompetencyExpertToolsService expertToolsService;

    private final CompetencyMappingToolsService mapperToolsService;

    private final ExerciseMappingToolsService exerciseMapperToolsService;

    public AtlasAgentToolCallbackService(CompetencyExpertToolsService expertToolsService, CompetencyMappingToolsService mapperToolsService,
            ExerciseMappingToolsService exerciseMapperToolsService) {
        this.expertToolsService = expertToolsService;
        this.mapperToolsService = mapperToolsService;
        this.exerciseMapperToolsService = exerciseMapperToolsService;
    }

    /**
     * Creates a provider exposing the Main Agent tools (information retrieval and delegation).
     *
     * @param toolsService the tools service providing main agent tools
     * @return ToolCallbackProvider for the Main Agent
     */
    public ToolCallbackProvider createMainAgentProvider(AtlasAgentToolsService toolsService) {
        return MethodToolCallbackProvider.builder().toolObjects(toolsService).build();
    }

    /**
     * Creates a provider exposing the Competency Expert sub-agent tools.
     *
     * @return ToolCallbackProvider for the Competency Expert
     */
    public ToolCallbackProvider createCompetencyExpertProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(expertToolsService).build();
    }

    /**
     * Creates a provider exposing the Competency Mapper sub-agent tools.
     *
     * @return ToolCallbackProvider for the Competency Mapper
     */
    public ToolCallbackProvider createCompetencyMapperProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(mapperToolsService).build();
    }

    /**
     * Creates a provider exposing the Exercise Mapper sub-agent tools.
     *
     * @return ToolCallbackProvider for the Exercise Mapper
     */
    public ToolCallbackProvider createExerciseMapperProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(exerciseMapperToolsService).build();
    }
}
