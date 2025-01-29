package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.iris.service.IrisConsistencyCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * REST controller for checking consistency of exercises.
 */
@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/iris/consistency-check/")
public class IrisConsistencyCheckResource {

    private static final Logger log = LoggerFactory.getLogger(IrisConsistencyCheckResource.class);

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<IrisConsistencyCheckService> irisConsistencyCheckService;

    public IrisConsistencyCheckResource(UserRepository userRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository,
            Optional<IrisConsistencyCheckService> irisConsistencyCheckService) {
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.irisConsistencyCheckService = irisConsistencyCheckService;

    }

    /**
     * POST /api/iris/consistency-check/exercises/{exerciseId} : Check the consistency of an exercise.
     *
     * @param exerciseId the id of the exercise to check
     * @return the ResponseEntity with status 200 (OK)
     */
    @EnforceAtLeastEditorInExercise
    @PostMapping("exercises/{exerciseId}")
    @Transactional
    public ResponseEntity<Void> consistencyCheckExercise(@PathVariable Long exerciseId) {
        var consistencyCheckService = irisConsistencyCheckService.orElseThrow();
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        consistencyCheckService.executeConsistencyCheckPipeline(user, (ProgrammingExercise) exercise);
        return ResponseEntity.ok().build();
    }
}
