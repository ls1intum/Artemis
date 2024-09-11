package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ConsistencyCheckService;
import de.tum.cit.aet.artemis.service.dto.ConsistencyErrorDTO;

/**
 * REST controller for consistency checks
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ConsistencyCheckResource {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyCheckResource.class);

    private final AuthorizationCheckService authCheckService;

    private final ConsistencyCheckService consistencyCheckService;

    private final ExerciseRepository exerciseRepository;

    public ConsistencyCheckResource(AuthorizationCheckService authCheckService, ConsistencyCheckService consistencyCheckService, ExerciseRepository exerciseRepository) {
        this.authCheckService = authCheckService;
        this.consistencyCheckService = consistencyCheckService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * GET programming-exercises/{programmingExerciseId}/consistency-check : request consistency check for a programming exercise
     *
     * @param programmingExerciseId id of the exercise to check
     * @return List containing the resulting errors, if any.
     */
    @GetMapping("programming-exercises/{programmingExerciseId}/consistency-check")
    @EnforceAtLeastEditor
    public ResponseEntity<List<ConsistencyErrorDTO>> checkConsistencyOfProgrammingExercise(@PathVariable long programmingExerciseId) {
        log.debug("REST request to check consistencies of programming exercise [{}]", programmingExerciseId);
        final Exercise exercise = exerciseRepository.findByIdElseThrow(programmingExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        List<ConsistencyErrorDTO> result = consistencyCheckService.checkConsistencyOfProgrammingExercise(programmingExerciseId);
        return ResponseEntity.ok(result);
    }
}
