package de.tum.cit.aet.artemis.atlas.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.dto.OrchestratorDefaultsDTO;
import de.tum.cit.aet.artemis.atlas.service.CompetencyOrchestrationService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;

/**
 * REST controller for the autonomous competency management orchestrator.
 * <p>
 * Availability is gated by the Atlas module ({@link AtlasEnabled}) plus the runtime
 * {@link Feature#AtlasAgent} feature toggle — the same toggle that controls the Atlas Companion
 * chat agent. No separate orchestrator toggle exists.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@RestController
@RequestMapping("api/atlas/orchestrator/")
public class CompetencyOrchestrationResource {

    private static final Logger log = LoggerFactory.getLogger(CompetencyOrchestrationResource.class);

    private final CompetencyOrchestrationService competencyOrchestrationService;

    private final AtlasOrchestratorProperties orchestratorProperties;

    public CompetencyOrchestrationResource(CompetencyOrchestrationService competencyOrchestrationService, AtlasOrchestratorProperties orchestratorProperties) {
        this.competencyOrchestrationService = competencyOrchestrationService;
        this.orchestratorProperties = orchestratorProperties;
    }

    /**
     * {@code GET defaults} : the global default debounce window and daily run cap that a course's
     * per-course overrides fall back to when left empty. Surfaced to the course-settings form so it
     * can show the instructor what "use default" actually resolves to, rather than a blank value.
     *
     * @return {@code 200 OK} with the {@link OrchestratorDefaultsDTO}
     */
    @GetMapping("defaults")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.AtlasAgent)
    public ResponseEntity<OrchestratorDefaultsDTO> getDefaults() {
        return ResponseEntity.ok(new OrchestratorDefaultsDTO(orchestratorProperties.debounceWindowSeconds(), orchestratorProperties.maxDailyOrchestrations()));
    }

    @PostMapping("programming-exercises/{exerciseId}/run")
    @EnforceAtLeastInstructorInExercise
    @FeatureToggle(Feature.AtlasAgent)
    public ResponseEntity<CompetencyOrchestrationResultDTO> runForProgrammingExercise(@PathVariable Long exerciseId) {
        log.info("REST request to run Atlas orchestrator for programming exercise: {}", exerciseId);
        CompetencyOrchestrationResultDTO result = competencyOrchestrationService.runWithQueuedFlush(exerciseId);
        return ResponseEntity.status(httpStatusFor(result)).body(result);
    }

    /** Maps orchestration outcome to HTTP status so frontend error handling does not need to parse the response body. */
    private static HttpStatus httpStatusFor(CompetencyOrchestrationResultDTO result) {
        return switch (result.status()) {
            case SUCCESS, NO_OP -> HttpStatus.OK;
            case PARTIAL -> HttpStatus.MULTI_STATUS;
            case IN_PROGRESS -> HttpStatus.CONFLICT;
            case FAILED -> switch (result.failureReason()) {
                case NO_CHAT_CLIENT -> HttpStatus.SERVICE_UNAVAILABLE;
                case LLM_ERROR -> HttpStatus.BAD_GATEWAY;
                case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
                case UNSUPPORTED_EXERCISE -> HttpStatus.UNPROCESSABLE_CONTENT;
            };
        };
    }
}
