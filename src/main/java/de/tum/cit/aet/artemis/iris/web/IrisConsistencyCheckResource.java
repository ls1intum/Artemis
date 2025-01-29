package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.iris.service.IrisConsistencyCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * REST controller for checking consistency of exercises.
 */
@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/")
public class IrisConsistencyCheckResource {

    private static final Logger log = LoggerFactory.getLogger(IrisConsistencyCheckResource.class);

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final Optional<IrisConsistencyCheckService> irisConsistencyCheckService;

    public IrisConsistencyCheckResource(UserRepository userRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository,
            Optional<IrisConsistencyCheckService> irisConsistencyCheckService) {
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.irisConsistencyCheckService = irisConsistencyCheckService;

    }

    @EnforceAtLeastTutorInCourse
    @PostMapping("exercises/{courseId}/{exerciseId}/consistency-check")
    public ResponseEntity<Void> consistencyCheckExercise(@PathVariable Long exerciseId, @PathVariable Long courseId) {
        var consistencyCheckService = irisConsistencyCheckService.orElseThrow();
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        consistencyCheckService.executeConsistencyCheckPipeline(user, course, (ProgrammingExercise) exercise);
        return ResponseEntity.ok().build();
    }

}
