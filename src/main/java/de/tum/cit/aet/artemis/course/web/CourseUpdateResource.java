package de.tum.cit.aet.artemis.course.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.ConductAgreementService;
import de.tum.cit.aet.artemis.atlas.api.CourseAutoOrchestrationApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.api.LearningPathApi;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.course.config.CourseLegacyRestPaths;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseUpdateDTO;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseValidator;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.CourseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupChannelManagementApi;

/**
 * REST controller for updating a course.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage("management/course-management")
@RestController
@SuppressWarnings("deprecation")
@RequestMapping({ "api/course/", CourseLegacyRestPaths.CORE_PREFIX })
public class CourseUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(CourseUpdateResource.class);

    private static final int MAX_TITLE_LENGTH = 255;

    private final AuthorizationCheckService authCheckService;

    private final FileService fileService;

    private final ConductAgreementService conductAgreementService;

    private final Optional<LtiApi> ltiApi;

    private final Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<LearningPathApi> learningPathApi;

    private final Optional<CourseAutoOrchestrationApi> autoOrchestrationApi;

    private final CourseRepository courseRepository;

    private final CourseConfigurationRepository courseConfigurationRepository;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService;

    private final InstanceMessageSendService instanceMessageSendService;

    public CourseUpdateResource(Optional<LtiApi> ltiApi, AuthorizationCheckService authCheckService, FileService fileService,
            Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi, Optional<LearningPathApi> learningPathApi,
            ConductAgreementService conductAgreementService, Optional<LearnerProfileApi> learnerProfileApi, Optional<CourseAutoOrchestrationApi> autoOrchestrationApi,
            CourseRepository courseRepository, CourseConfigurationRepository courseConfigurationRepository, ExerciseRepository exerciseRepository, UserRepository userRepository,
            Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService, InstanceMessageSendService instanceMessageSendService) {
        this.ltiApi = ltiApi;
        this.authCheckService = authCheckService;
        this.fileService = fileService;
        this.tutorialGroupChannelManagementApi = tutorialGroupChannelManagementApi;
        this.learningPathApi = learningPathApi;
        this.autoOrchestrationApi = autoOrchestrationApi;
        this.conductAgreementService = conductAgreementService;
        this.learnerProfileApi = learnerProfileApi;
        this.courseRepository = courseRepository;
        this.courseConfigurationRepository = courseConfigurationRepository;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * PUT /courses/:courseId : Updates an existing course.
     *
     * @param courseId        the id of the course to update
     * @param courseUpdateDTO the DTO containing the course update data
     * @param file            the optional course icon file
     * @return the ResponseEntity with status 200 (OK) and with body the updated course
     */
    @PutMapping(value = "courses/{courseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastInstructor
    public ResponseEntity<Course> updateCourse(@PathVariable Long courseId, @RequestPart("course") CourseUpdateDTO courseUpdateDTO,
            @RequestPart(required = false) MultipartFile file) throws URISyntaxException {
        log.debug("REST request to update Course : {}", courseUpdateDTO);
        User user = userRepository.getUserWithAuthorities();

        // Always use the path variable for lookups to prevent a DTO with a mismatched id
        // from loading (and potentially modifying) a different course than the URL indicates
        var existingCourse = courseRepository.findByIdForUpdateElseThrow(courseId);
        // athenaConfig is not included in the findForUpdateById EntityGraph; load it separately to avoid LazyInitializationException in courseUpdateDTO.applyTo()
        existingCourse.setAthenaConfig(courseRepository.findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(courseId).getAthenaConfig());

        // Attach the (lazily-stored) course configuration so applyTo updates it in place instead of creating a duplicate,
        // and so the admin-only auto-orchestration change detection below compares against the persisted values. Fetched
        // via its own repository to keep the course update entity graph small.
        existingCourse.setCourseConfiguration(courseConfigurationRepository.findByCourseId(courseId).orElse(null));

        if (existingCourse.getTimeZone() != null && courseUpdateDTO.timeZone() == null) {
            throw new IllegalArgumentException("You can not remove the time zone of a course");
        }

        var timeZoneChanged = (existingCourse.getTimeZone() != null && courseUpdateDTO.timeZone() != null && !existingCourse.getTimeZone().equals(courseUpdateDTO.timeZone()));

        if (!Objects.equals(existingCourse.getShortName(), courseUpdateDTO.shortName())) {
            throw new BadRequestAlertException("The course short name cannot be changed", Course.ENTITY_NAME, "shortNameCannotChange", true);
        }

        // only allow admins or instructors of the existing course to change it
        // this is important, otherwise someone could put themselves into the instructor group of the updated course
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, existingCourse, user);

        if (!authCheckService.isCurrentUserAdminAccessEnabled()) {
            // instructors are not allowed to change the Atlas auto-orchestration settings (admin-only)
            boolean autoOrchestrationChanged = existingCourse.getAutoOrchestratorEnabled() != courseUpdateDTO.autoOrchestratorEnabled()
                    || !Objects.equals(existingCourse.getDebounceWindowSecondsOverride(), courseUpdateDTO.debounceWindowSecondsOverride())
                    || !Objects.equals(existingCourse.getMaxDailyOrchestrationOverride(), courseUpdateDTO.maxDailyOrchestrationOverride());
            if (autoOrchestrationChanged) {
                throw new BadRequestAlertException("You are not allowed to change the auto-orchestration settings of a course", Course.ENTITY_NAME,
                        "autoOrchestrationSettingsCannotChange", true);
            }
        }

        if (courseUpdateDTO.title().length() > MAX_TITLE_LENGTH) {
            throw new BadRequestAlertException("The course title is too long", Course.ENTITY_NAME, "courseTitleTooLong");
        }

        // Save the existing course icon path before applying DTO changes
        String existingCourseIcon = existingCourse.getCourseIcon();
        // Save values that are checked AFTER applyTo mutates the entity
        boolean oldLearningPathsEnabled = existingCourse.getLearningPathsEnabled();
        boolean oldAutoOrchestratorEnabled = existingCourse.getAutoOrchestratorEnabled();
        String oldCodeOfConduct = existingCourse.getCourseInformationSharingMessagingCodeOfConduct();
        boolean oldGradingFeedbackEnabled = existingCourse.getAthenaConfig() != null && existingCourse.getAthenaConfig().isGradingFeedbackEnabled();

        // Apply DTO values to the existing course entity - this preserves all relationships
        courseUpdateDTO.applyTo(existingCourse);
        existingCourse.setId(courseId); // Ensure the ID is correct

        CourseValidator.validateEnrollmentConfirmationMessage(existingCourse);
        CourseValidator.validateComplaintsAndRequestMoreFeedbackConfig(existingCourse);
        CourseValidator.validateOnlineCourseAndEnrollmentEnabled(existingCourse);
        CourseValidator.validateShortName(existingCourse);
        CourseValidator.validateAccuracyOfScores(existingCourse);
        CourseValidator.validatePointBounds(existingCourse);
        CourseValidator.validateStartAndEndDate(existingCourse);
        CourseValidator.validateEnrollmentStartAndEndDate(existingCourse);
        CourseValidator.validateUnenrollmentEndDate(existingCourse);
        if (file != null) {
            Path basePath = FilePathConverter.getCourseIconFilePath();
            Path savePath = FileUtil.saveFile(file, basePath, FilePathType.COURSE_ICON, false);
            existingCourse.setCourseIcon(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.COURSE_ICON, courseId).toString());
            if (existingCourseIcon != null) {
                // delete old course icon
                fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(new URI(existingCourseIcon), FilePathType.COURSE_ICON), 0);
            }
        }
        else if (courseUpdateDTO.courseIcon() == null && existingCourseIcon != null) {
            // delete old course icon
            fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(new URI(existingCourseIcon), FilePathType.COURSE_ICON), 0);
        }

        boolean wasOnlineCourse = existingCourse.getOnlineCourseConfiguration() != null;
        if (courseUpdateDTO.onlineCourse() != null && courseUpdateDTO.onlineCourse() != wasOnlineCourse) {
            if (courseUpdateDTO.onlineCourse() && ltiApi.isPresent()) {
                ltiApi.get().createOnlineCourseConfiguration(existingCourse);
            }
            else {
                existingCourse.setOnlineCourseConfiguration(null);
            }
        }

        if (!Objects.equals(courseUpdateDTO.courseInformationSharingMessagingCodeOfConduct(), oldCodeOfConduct)) {
            conductAgreementService.resetUsersAgreeToCodeOfConductInCourse(existingCourse);
        }

        Course result = courseRepository.save(existingCourse);

        // If auto-orchestration was just disabled, drop any buffered content changes so a stale batch cannot fire
        // (e.g. on re-enable within the debounce window or a scheduler tick before the change propagates).
        if (oldAutoOrchestratorEnabled && !courseUpdateDTO.autoOrchestratorEnabled()) {
            autoOrchestrationApi.ifPresent(api -> api.flushBufferedContentChanges(courseId));
        }

        searchableEntityWeaviateService.ifPresent(service -> service.upsertCourseAsync(CourseSearchableEntityDTO.fromCourse(result)));

        // if learning paths got enabled, generate learning paths for students
        if (!oldLearningPathsEnabled && courseUpdateDTO.learningPathsEnabled() && learningPathApi.isPresent()) {
            Course courseWithCompetencies = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(result.getId());
            Set<User> students = userRepository.getStudentsWithLearnerProfile(courseWithCompetencies);
            learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfiles(courseWithCompetencies, students));
            learningPathApi.ifPresent(api -> api.generateLearningPaths(courseWithCompetencies));
        }

        if (timeZoneChanged && tutorialGroupChannelManagementApi.isPresent()) {
            tutorialGroupChannelManagementApi.get().onTimeZoneUpdate(result);
        }

        boolean newGradingFeedbackEnabled = result.getAthenaConfig() != null && result.getAthenaConfig().isGradingFeedbackEnabled();
        if (oldGradingFeedbackEnabled != newGradingFeedbackEnabled) {
            refreshAthenaSchedulingForCourseExercises(courseId);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Publishes a scheduling refresh for every exercise of the course whose type is wired for Athena due-date scheduling
     * (see {@code AthenaScheduleService}), so the scheduling node creates or cancels each Athena task based on the
     * course's current grading feedback configuration. Without this, enabling the flag would leave already-existing
     * exercises unscheduled until the next server restart, and disabling it would leave already-scheduled tasks running.
     *
     * @param courseId the id of the course whose Athena grading feedback flag was just changed
     */
    private void refreshAthenaSchedulingForCourseExercises(Long courseId) {
        for (Exercise exercise : exerciseRepository.findAllAthenaSchedulableExercisesWithFutureDueDateByCourseId(courseId)) {
            switch (exercise) {
                case ProgrammingExercise programmingExercise -> instanceMessageSendService.sendProgrammingExerciseSchedule(programmingExercise.getId());
                case TextExercise textExercise -> instanceMessageSendService.sendTextExerciseSchedule(textExercise.getId());
                default -> {
                    // no other exercise type is currently wired for Athena due-date scheduling
                }
            }
        }
    }

}
