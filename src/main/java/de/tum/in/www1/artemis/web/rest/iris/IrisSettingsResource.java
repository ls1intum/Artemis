package de.tum.in.www1.artemis.web.rest.iris;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;

/**
 * REST controller for managing {@link IrisSettings}.
 */
@RestController
@RequestMapping("api/")
public class IrisSettingsResource {

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
     * GET iris/global-iris-settings: Retrieve the raw iris settings for the course.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the settings.
     */
    @GetMapping("iris/global-iris-settings")
    @EnforceAtLeastInstructor
    public ResponseEntity<IrisSettings> getGlobalSettings() {
        var irisSettings = irisSettingsService.getGlobalSettings();
        return ResponseEntity.ok(irisSettings);
    }

    /**
     * GET courses/{courseId}/raw-iris-settings: Retrieve the raw iris settings for the course.
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the settings, or with status {@code 404 (Not Found)} if the course could not be found.
     */
    @GetMapping("courses/{courseId}/raw-iris-settings")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisSettings> getRawCourseSettings(@PathVariable Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        var irisSettings = irisSettingsService.getIrisSettingsOrDefault(course);
        return ResponseEntity.ok(irisSettings);
    }

    /**
     * GET programming-exercises/{exerciseId}/raw-iris-settings: Retrieve the raw iris settings for the programming exercise.
     *
     * @param exerciseId of the programming exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the settings, or with status {@code 404 (Not Found)} if the exercise could not be found.
     */
    @GetMapping("programming-exercises/{exerciseId}/raw-iris-settings")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisSettings> getRawProgrammingExerciseSettings(@PathVariable Long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var combinedIrisSettings = irisSettingsService.getIrisSettingsOrDefault(exercise);
        return ResponseEntity.ok(combinedIrisSettings);
    }

    /**
     * GET courses/{courseId}/iris-settings: Retrieve the actual iris settings for the course.
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the settings, or with status {@code 404 (Not Found)} if the course could not be found.
     */
    @GetMapping("courses/{courseId}/iris-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisSettings> getCourseSettings(@PathVariable Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // Editors can see the full settings, students only the reduced settings
        var getReduced = !authCheckService.isAtLeastEditorInCourse(course, user);
        var irisSettings = irisSettingsService.getCombinedIrisSettings(course, getReduced);
        return ResponseEntity.ok(irisSettings);
    }

    /**
     * GET programming-exercises/{exerciseId}/iris-settings: Retrieve the actual iris settings for the programming exercise.
     *
     * @param exerciseId of the programming exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the settings, or with status {@code 404 (Not Found)} if the exercise could not be found.
     */
    @GetMapping("programming-exercises/{exerciseId}/iris-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisSettings> getProgrammingExerciseSettings(@PathVariable Long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        // Editors can see the full settings, students only the reduced settings
        var getReduced = !authCheckService.isAtLeastEditorForExercise(exercise, user);
        var combinedIrisSettings = irisSettingsService.getCombinedIrisSettings(exercise, getReduced);
        return ResponseEntity.ok(combinedIrisSettings);
    }

    /**
     * PUT courses/{courseId}/raw-iris-settings: Update the raw iris settings for the course.
     *
     * @param courseId of the course
     * @param settings the settings to update
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated settings, or with status {@code 404 (Not Found)} if the course could not be found.
     */
    @PutMapping("courses/{courseId}/raw-iris-settings")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisSettings> updateCourseSettings(@PathVariable Long courseId, @RequestBody IrisSettings settings) {
        var course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        var updatedSettings = irisSettingsService.saveIrisSettings(course, settings);
        return ResponseEntity.ok(updatedSettings);
    }

    /**
     * PUT programming-exercises/{exerciseId}/raw-iris-settings: Update the raw iris settings for the programming exercise.
     *
     * @param exerciseId of the programming exercise
     * @param settings   the settings to update
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated settings, or with status {@code 404 (Not Found)} if the exercise could not be
     *         found.
     */
    @PutMapping("programming-exercises/{exerciseId}/raw-iris-settings")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisSettings> updateProgrammingExerciseSettings(@PathVariable Long exerciseId, @RequestBody IrisSettings settings) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);
        var updatedSettings = irisSettingsService.saveIrisSettings(exercise, settings);
        return ResponseEntity.ok(updatedSettings);
    }
}
