package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.atlas.service.AssignerToolsService;
import de.tum.cit.aet.artemis.atlas.service.AtlasAgentToolsService;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService;
import de.tum.cit.aet.artemis.atlas.service.CompetencyMappingToolsService;
import de.tum.cit.aet.artemis.atlas.service.CreatorToolsService;
import de.tum.cit.aet.artemis.atlas.service.EditorToolsService;
import de.tum.cit.aet.artemis.atlas.service.ExerciseMappingToolsService;
import de.tum.cit.aet.artemis.atlas.service.OrchestratorPlanningToolsService;
import de.tum.cit.aet.artemis.atlas.service.OrchestratorReadToolsService;

/**
 * Central wiring of the Atlas tool surfaces into per-role {@link AtlasToolSurface} beans.
 * <p>
 * Each agent or worker gets exactly one surface bean wrapping a {@link ToolCallbackProvider} that
 * exposes the {@code @Tool}-annotated methods of a single tool service. Keeping the wiring here —
 * rather than building providers inside the consuming services — gives the orchestrator and chat
 * agents a narrow, explicit tool surface per role and lets the autonomous orchestrator compose its
 * read and planning surfaces independently of the chat agents.
 * <p>
 * The providers are deliberately wrapped in {@link AtlasToolSurface} rather than exposed as
 * {@link ToolCallbackProvider} beans: Spring AI's {@code ToolCallingAutoConfiguration} auto-discovers
 * every {@code ToolCallbackProvider} bean and would both flatten all role surfaces into one global
 * tool set and create a circular dependency back to the chat stack. See {@link AtlasToolSurface}.
 * <p>
 * Beans are {@link Lazy} so the providers (and the tool services behind them) are only instantiated
 * when an Atlas flow actually runs. They are qualified by name so consumers can inject precisely the
 * surface they need.
 */
@Lazy
@Configuration
@Conditional(AtlasEnabled.class)
public class AtlasAgentToolConfig {

    // ---------------------------------------------------------------------------------------------
    // Chat-agent tool surfaces
    // ---------------------------------------------------------------------------------------------

    /**
     * Tools for the Main (chat) Agent: course-information retrieval and delegation to the sub-agents.
     *
     * @param toolsService the main-agent tool service
     * @return the main-agent tool surface
     */
    @Bean
    @Lazy
    @Qualifier("mainAgentToolCallbackProvider")
    public AtlasToolSurface mainAgentToolCallbackProvider(AtlasAgentToolsService toolsService) {
        return new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(toolsService).build());
    }

    /**
     * Tools for the Competency Expert sub-agent (preview / create / update competencies).
     *
     * @param expertToolsService the competency-expert tool service
     * @return the competency-expert tool surface
     */
    @Bean
    @Lazy
    @Qualifier("competencyExpertToolCallbackProvider")
    public AtlasToolSurface competencyExpertToolCallbackProvider(CompetencyExpertToolsService expertToolsService) {
        return new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(expertToolsService).build());
    }

    /**
     * Tools for the Competency Mapper sub-agent (relation mapping and preview).
     *
     * @param mapperToolsService the competency-mapper tool service
     * @return the competency-mapper tool surface
     */
    @Bean
    @Lazy
    @Qualifier("competencyMapperToolCallbackProvider")
    public AtlasToolSurface competencyMapperToolCallbackProvider(CompetencyMappingToolsService mapperToolsService) {
        return new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(mapperToolsService).build());
    }

    /**
     * Tools for the Exercise Mapper sub-agent (exercise listing and exercise-to-competency mapping).
     *
     * @param exerciseMapperToolsService the exercise-mapper tool service
     * @return the exercise-mapper tool surface
     */
    @Bean
    @Lazy
    @Qualifier("exerciseMapperToolCallbackProvider")
    public AtlasToolSurface exerciseMapperToolCallbackProvider(ExerciseMappingToolsService exerciseMapperToolsService) {
        return new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(exerciseMapperToolsService).build());
    }

    // ---------------------------------------------------------------------------------------------
    // Autonomous orchestrator tool surfaces
    // ---------------------------------------------------------------------------------------------

    /**
     * Read-only orchestrator tools ({@code getCompetencyDetails}, {@code getExerciseContent}) used by
     * the orchestrator to inspect course state. Kept separate from the batch-planning read so it can
     * be composed independently.
     *
     * @param service the orchestrator read tool service
     * @return the orchestrator read tool surface
     */
    @Bean
    @Lazy
    @Qualifier("orchestratorReadToolCallbackProvider")
    public AtlasToolSurface orchestratorReadToolCallbackProvider(OrchestratorReadToolsService service) {
        return new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(service).build());
    }

    /**
     * Batch-planning orchestrator tool ({@code listCompetencyIndex}) used by the orchestrator to plan
     * the batch of competency-management actions.
     *
     * @param service the orchestrator planning tool service
     * @return the orchestrator planning tool surface
     */
    @Bean
    @Lazy
    @Qualifier("orchestratorPlanningToolCallbackProvider")
    public AtlasToolSurface orchestratorPlanningToolCallbackProvider(OrchestratorPlanningToolsService service) {
        return new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(service).build());
    }

    /**
     * Creator write tool ({@code createCompetency}).
     *
     * @param service the creator tool service
     * @return the creator tool surface
     */
    @Bean
    @Lazy
    @Qualifier("creatorToolCallbackProvider")
    public AtlasToolSurface creatorToolCallbackProvider(CreatorToolsService service) {
        return new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(service).build());
    }

    /**
     * Editor write tools ({@code editCompetency}, {@code deleteCompetency}).
     *
     * @param service the editor tool service
     * @return the editor tool surface
     */
    @Bean
    @Lazy
    @Qualifier("editorToolCallbackProvider")
    public AtlasToolSurface editorToolCallbackProvider(EditorToolsService service) {
        return new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(service).build());
    }

    /**
     * Assigner write tools ({@code assignExerciseToCompetency}, {@code unassignExerciseFromCompetency}).
     *
     * @param service the assigner tool service
     * @return the assigner tool surface
     */
    @Bean
    @Lazy
    @Qualifier("assignerToolCallbackProvider")
    public AtlasToolSurface assignerToolCallbackProvider(AssignerToolsService service) {
        return new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(service).build());
    }
}
