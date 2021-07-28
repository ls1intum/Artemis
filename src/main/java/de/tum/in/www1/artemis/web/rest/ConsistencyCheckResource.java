package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.service.ConsistencyCheckService;
import de.tum.in.www1.artemis.service.dto.ConsistencyErrorDTO;

/**
 * REST controller for consistency checks
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class ConsistencyCheckResource {

    private final Logger log = LoggerFactory.getLogger(ConsistencyCheckResource.class);

    private final ConsistencyCheckService consistencyCheckService;

    public ConsistencyCheckResource(ConsistencyCheckService consistencyCheckService) {
        this.consistencyCheckService = consistencyCheckService;
    }

    /**
     * GET /consistency-check/exercise/{programmingExerciseId} : request consistency check of a programming exercise
     * @param programmingExerciseId id of the exercise to check
     * @return List containing the resulting errors, if any.
     */
    @GetMapping("/consistency-check/exercise/{programmingExerciseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ConsistencyErrorDTO>> checkConsistencyOfProgrammingExercise(@PathVariable long programmingExerciseId) {
        log.debug("REST request to check consistencies of programming exercise [{}]", programmingExerciseId);
        List<ConsistencyErrorDTO> result = consistencyCheckService.checkConsistencyOfProgrammingExercise(programmingExerciseId);

        return ResponseEntity.ok(result);
    }

    /**
     * GET /consistency-check/course/{courseId} : request consistency check of all programming exercises of a given course
     * @param courseId id of the course to check
     * @return List containing the resulting errors, if any.
     */
    @GetMapping("/consistency-check/course/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ConsistencyErrorDTO>> checkConsistencyOfCourse(@PathVariable long courseId) {
        log.debug("REST request to check consistencies of course [{}]", courseId);
        List<ConsistencyErrorDTO> result = consistencyCheckService.checkConsistencyOfCourse(courseId);

        return ResponseEntity.ok(result);
    }
}
