package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.programming.dto.ConsistencyErrorDTO;
import de.tum.cit.aet.artemis.programming.service.ConsistencyCheckService;

/**
 * REST controller for consistency checks
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.PROGRAMMING_CONSISTENCY_CHECK)
@RestController
@RequestMapping("api/exercise/")
public class ConsistencyCheckResource {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyCheckResource.class);

    private final ConsistencyCheckService consistencyCheckService;

    public ConsistencyCheckResource(ConsistencyCheckService consistencyCheckService) {
        this.consistencyCheckService = consistencyCheckService;
    }

    /**
     * GET programming-exercises/{programmingExerciseId}/consistency-check : request consistency check for a programming exercise
     *
     * @param programmingExerciseId id of the exercise to check
     * @return List containing the resulting errors, if any.
     */
    @UsageInteraction(FeatureInteraction.ACTION)
    @GetMapping("programming-exercises/{programmingExerciseId}/consistency-check")
    @EnforceAtLeastEditorInExercise(resourceIdFieldName = "programmingExerciseId")
    public ResponseEntity<List<ConsistencyErrorDTO>> checkConsistencyOfProgrammingExercise(@PathVariable long programmingExerciseId) {
        log.debug("REST request to check consistencies of programming exercise [{}]", programmingExerciseId);
        List<ConsistencyErrorDTO> result = consistencyCheckService.checkConsistencyOfProgrammingExercise(programmingExerciseId);
        return ResponseEntity.ok(result);
    }
}
