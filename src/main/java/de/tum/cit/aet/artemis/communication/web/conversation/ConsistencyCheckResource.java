package de.tum.cit.aet.artemis.communication.web.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.iris.service.IrisConsistencyCheckService;

/**
 * REST controller for checking consistency of exercises.
 */
@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/")
public class ConsistencyCheckResource {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyCheckResource.class);

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<IrisConsistencyCheckService> irisConsistencyCheckService;

    public ConsistencyCheckResource(UserRepository userRepository, ExerciseRepository exerciseRepository, Optional<IrisConsistencyCheckService> irisConsistencyCheckService) {
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.irisConsistencyCheckService = irisConsistencyCheckService;

    }

    @EnforceAtLeastTutorInCourse
    @PostMapping("exercises/{exerciseId}/consistency-check")
    public ResponseEntity<Void> consistencyCheckExercise(@RequestParam String toBeChecked, @PathVariable Long exerciseId) {
        var consistencyCheckService = irisConsistencyCheckService.orElseThrow();
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        consistencyCheckService.executeConsistencyCheckPipeline(user, exercise, toBeChecked);
        log.debug("REST request to check consistency of exercise: {}", toBeChecked);
        return ResponseEntity.ok().build();
    }

}
