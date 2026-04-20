package de.tum.cit.aet.artemis.atlas.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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

    private final CompetencyOrchestrationService competencyOrchestrationService;

    public CompetencyOrchestrationResource(CompetencyOrchestrationService competencyOrchestrationService) {
        this.competencyOrchestrationService = competencyOrchestrationService;
    }

    @PostMapping("programming-exercises/{exerciseId}/run")
    @EnforceAtLeastInstructorInExercise
    @FeatureToggle(Feature.AtlasAgent)
    public ResponseEntity<CompetencyOrchestrationResultDTO> runForProgrammingExercise(@PathVariable Long exerciseId) {
        CompetencyOrchestrationResultDTO result = competencyOrchestrationService.run(exerciseId);
        return ResponseEntity.ok(result);
    }
}
