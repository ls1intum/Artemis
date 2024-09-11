package de.tum.cit.aet.artemis.web.rest.iris;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisExerciseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing {@link IrisSettings}.
 */
@Profile("iris")
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
        var irisSettings = irisSettingsService.getRawIrisSettingsFor(course);
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

        var combinedIrisSettings = irisSettingsService.getRawIrisSettingsFor(exercise);
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
    public ResponseEntity<IrisCombinedSettingsDTO> getCourseSettings(@PathVariable Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // Editors can see the full settings, students only the reduced settings
        var getReduced = !authCheckService.isAtLeastEditorInCourse(course, user);
        var irisSettings = irisSettingsService.getCombinedIrisSettingsFor(course, getReduced);
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
    public ResponseEntity<IrisCombinedSettingsDTO> getProgrammingExerciseSettings(@PathVariable Long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var combinedIrisSettings = irisSettingsService.getCombinedIrisSettingsFor(exercise, irisSettingsService.shouldShowMinimalSettings(exercise, user));
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
    public ResponseEntity<IrisCourseSettings> updateCourseSettings(@PathVariable Long courseId, @RequestBody IrisCourseSettings settings) {
        var course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        settings.setCourse(course);
        var updatedSettings = irisSettingsService.saveIrisSettings(settings);
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
    @EnforceAtLeastInstructor
    public ResponseEntity<IrisExerciseSettings> updateProgrammingExerciseSettings(@PathVariable Long exerciseId, @RequestBody IrisExerciseSettings settings) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);
        settings.setExercise(exercise);
        var updatedSettings = irisSettingsService.saveIrisSettings(settings);
        return ResponseEntity.ok(updatedSettings);
    }
}
