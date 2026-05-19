package de.tum.cit.aet.artemis.atlas.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.service.CompetencyOrchestrationService;
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

    public CompetencyOrchestrationResource(CompetencyOrchestrationService competencyOrchestrationService) {
        this.competencyOrchestrationService = competencyOrchestrationService;
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
            case SUCCESS -> HttpStatus.OK;
            case IN_PROGRESS -> HttpStatus.CONFLICT;
            case FAILED -> switch (result.failureReason()) {
                case NO_CHAT_CLIENT -> HttpStatus.SERVICE_UNAVAILABLE;
                case LLM_ERROR -> HttpStatus.BAD_GATEWAY;
                case UNSUPPORTED_EXERCISE -> HttpStatus.UNPROCESSABLE_CONTENT;
            };
        };
    }
}
