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

import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ConsistencyCheckService;
import de.tum.in.www1.artemis.service.dto.ConsistencyErrorDTO;

/**
 * REST controller for consistency checks
 */
@RestController
@RequestMapping("api")
@PreAuthorize("hasRole('INSTRUCTOR')")
public class ConsistencyCheckResource {

    private final Logger log = LoggerFactory.getLogger(ConsistencyCheckResource.class);

    private final AuthorizationCheckService authCheckService;

    private final ConsistencyCheckService consistencyCheckService;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    public ConsistencyCheckResource(AuthorizationCheckService authCheckService, ConsistencyCheckService consistencyCheckService, ExerciseRepository exerciseRepository,
            CourseRepository courseRepository, UserRepository userRepository) {
        this.authCheckService = authCheckService;
        this.consistencyCheckService = consistencyCheckService;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    /**
     * GET /consistency-check/exercise/{programmingExerciseId} : request consistency check of a programming exercise
     * @param programmingExerciseId id of the exercise to check
     * @return List containing the resulting errors, if any.
     */
    @GetMapping("/consistency-check/exercise/{programmingExerciseId}")
    public ResponseEntity<List<ConsistencyErrorDTO>> checkConsistencyOfProgrammingExercise(@PathVariable long programmingExerciseId) {
        log.debug("REST request to check consistencies of programming exercise [{}]", programmingExerciseId);
        if (authCheckService.isAtLeastInstructorForExercise(exerciseRepository.findByIdElseThrow(programmingExerciseId))) {
            List<ConsistencyErrorDTO> result = consistencyCheckService.checkConsistencyOfProgrammingExercise(programmingExerciseId);

            return ResponseEntity.ok(result);
        }
        else {
            return ResponseEntity.status(401).body(null);
        }

    }

    /**
     * GET /consistency-check/course/{courseId} : request consistency check of all programming exercises of a given course
     * @param courseId id of the course to check
     * @return List containing the resulting errors, if any.
     */
    @GetMapping("/consistency-check/course/{courseId}")
    public ResponseEntity<List<ConsistencyErrorDTO>> checkConsistencyOfCourse(@PathVariable long courseId) {
        log.debug("REST request to check consistencies of course [{}]", courseId);
        if (authCheckService.isAtLeastInstructorInCourse(courseRepository.findByIdElseThrow(courseId), userRepository.getUserWithGroupsAndAuthorities())) {
            List<ConsistencyErrorDTO> result = consistencyCheckService.checkConsistencyOfCourse(courseId);

            return ResponseEntity.ok(result);
        }
        else {
            return ResponseEntity.status(401).body(null);
        }
    }
}
