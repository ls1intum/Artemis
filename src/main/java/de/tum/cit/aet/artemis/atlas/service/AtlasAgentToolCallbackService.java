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

    private final ToolCallbackProvider expertProvider;

    private final ToolCallbackProvider mapperProvider;

    private final ToolCallbackProvider exerciseMapperProvider;

    private volatile ToolCallbackProvider mainAgentProvider;

    public AtlasAgentToolCallbackService(CompetencyExpertToolsService expertToolsService, CompetencyMappingToolsService mapperToolsService,
            ExerciseMappingToolsService exerciseMapperToolsService) {
        this.expertProvider = MethodToolCallbackProvider.builder().toolObjects(expertToolsService).build();
        this.mapperProvider = MethodToolCallbackProvider.builder().toolObjects(mapperToolsService).build();
        this.exerciseMapperProvider = MethodToolCallbackProvider.builder().toolObjects(exerciseMapperToolsService).build();
    }

    /**
     * Returns the provider exposing the Main Agent tools (information retrieval and delegation).
     * Cached after first call. Accepts the tools service as a parameter to avoid a circular bean dependency.
     *
     * @param toolsService the tools service providing main agent tools
     * @return ToolCallbackProvider for the Main Agent
     */
    public ToolCallbackProvider createMainAgentProvider(AtlasAgentToolsService toolsService) {
        if (mainAgentProvider == null) {
            mainAgentProvider = MethodToolCallbackProvider.builder().toolObjects(toolsService).build();
        }
        return mainAgentProvider;
    }

    /**
     * Returns the provider exposing the Competency Expert sub-agent tools.
     *
     * @return ToolCallbackProvider for the Competency Expert
     */
    public ToolCallbackProvider createCompetencyExpertProvider() {
        return expertProvider;
    }

    /**
     * Returns the provider exposing the Competency Mapper sub-agent tools.
     *
     * @return ToolCallbackProvider for the Competency Mapper
     */
    public ToolCallbackProvider createCompetencyMapperProvider() {
        return mapperProvider;
    }

    /**
     * Returns the provider exposing the Exercise Mapper sub-agent tools.
     *
     * @return ToolCallbackProvider for the Exercise Mapper
     */
    public ToolCallbackProvider createExerciseMapperProvider() {
        return exerciseMapperProvider;
    }
}
