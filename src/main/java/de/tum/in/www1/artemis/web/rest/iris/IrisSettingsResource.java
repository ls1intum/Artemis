package de.tum.in.www1.artemis.web.rest.iris;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;

/**
 * REST controller for managing {@link IrisSettings}.
 */
@RestController
@RequestMapping("api/")
public class IrisSettingsResource {

    private final Logger log = LoggerFactory.getLogger(IrisSettingsResource.class);

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public IrisSettingsResource(UserRepository userRepository, CourseRepository courseRepository, IrisSettingsService irisSettingsService,
            AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * GET courses/{courseId}/iris/settings: Retrieve the iris settings for the course.
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the settings, or with status {@code 404 (Not Found)} if the course could not be found.
     */
    @GetMapping("courses/{courseId}/iris/settings")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<IrisSettings> getCourseSettings(@PathVariable Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        var irisSettings = irisSettingsService.getIrisSettings(course);
        return ResponseEntity.ok(irisSettings);
    }

    /**
     * GET programming-exercises/{exerciseId}/iris/settings: Retrieve the iris settings for the programming exercise.
     *
     * @param exerciseId of the programming exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the settings, or with status {@code 404 (Not Found)} if the exercise could not be found.
     */
    @GetMapping("programming-exercises/{exerciseId}/iris/settings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IrisSettings> getProgrammingExerciseSettings(@PathVariable Long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        // Editors can see the full settings, students only the reduced settings
        var getReduced = !authCheckService.isAtLeastEditorForExercise(exercise, user);
        var combinedIrisSettings = irisSettingsService.getCombinedIrisSettings(exercise, getReduced);
        return ResponseEntity.ok(combinedIrisSettings);
    }
}
