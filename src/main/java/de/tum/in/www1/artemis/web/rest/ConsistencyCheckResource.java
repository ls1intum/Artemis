package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ConsistencyCheckService;
import de.tum.in.www1.artemis.service.dto.ConsistencyErrorDTO;

/**
 * REST controller for consistency checks
 */
@RestController
@PreAuthorize("hasRole('INSTRUCTOR')")
public class ConsistencyCheckResource {

    private final Logger log = LoggerFactory.getLogger(ConsistencyCheckResource.class);

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
     * @param programmingExerciseId id of the exercise to check
     * @return List containing the resulting errors, if any.
     */
    @GetMapping("programming-exercises/{programmingExerciseId}/consistency-check")
    public ResponseEntity<List<ConsistencyErrorDTO>> checkConsistencyOfProgrammingExercise(@PathVariable long programmingExerciseId) {
        log.debug("REST request to check consistencies of programming exercise [{}]", programmingExerciseId);
        final Exercise exercise = exerciseRepository.findByIdElseThrow(programmingExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        List<ConsistencyErrorDTO> result = consistencyCheckService.checkConsistencyOfProgrammingExercise(programmingExerciseId);
        return ResponseEntity.ok(result);
    }
}
