package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.service.ConsistencyCheckService;
import de.tum.in.www1.artemis.service.dto.ConsistencyErrorDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/consistency-check/exercise/{programmingExerciseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ConsistencyErrorDTO>> checkConsistencyOfProgrammingExercise(@PathVariable long programmingExerciseId) {
        List<ConsistencyErrorDTO> result = consistencyCheckService.checkConsistencyOfProgrammingExercise(programmingExerciseId);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/consistency-check/course/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ConsistencyErrorDTO>> checkConsistencyOfCourse(@PathVariable long courseId) {
        List<ConsistencyErrorDTO> result = consistencyCheckService.checkConsistencyOfCourse(courseId);

        return ResponseEntity.ok(result);
    }
}
