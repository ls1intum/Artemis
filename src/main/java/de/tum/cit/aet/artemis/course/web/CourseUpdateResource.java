package de.tum.cit.aet.artemis.course.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.util.DateUtil;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseManagementDTO;
import de.tum.cit.aet.artemis.course.dto.CourseUpdateDTO;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseValidator;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.CourseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupChannelManagementApi;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

/**
 * REST controller for updating a course.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.COURSE_SETTINGS)
@RestController
@RequestMapping("api/course/")
public class CourseUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(CourseUpdateResource.class);

    private static final int MAX_TITLE_LENGTH = 255;

    private final AuthorizationCheckService authCheckService;

    private final FileService fileService;

    private final ConductAgreementService conductAgreementService;

    private final Optional<LtiApi> ltiApi;

    private final Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<LearningPathApi> learningPathApi;

    private final Optional<CourseAutoOrchestrationApi> autoOrchestrationApi;

    private final CourseRepository courseRepository;

    private final CourseConfigurationRepository courseConfigurationRepository;

    private final CourseAthenaConfigRepository courseAthenaConfigRepository;

    private final UserRepository userRepository;

    private final Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService;

    public CourseUpdateResource(Optional<LtiApi> ltiApi, AuthorizationCheckService authCheckService, FileService fileService,
            Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi, Optional<LearningPathApi> learningPathApi,
            ConductAgreementService conductAgreementService, Optional<LearnerProfileApi> learnerProfileApi, Optional<CourseAutoOrchestrationApi> autoOrchestrationApi,
            CourseRepository courseRepository, CourseConfigurationRepository courseConfigurationRepository, CourseAthenaConfigRepository courseAthenaConfigRepository,
            UserRepository userRepository, Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService, Optional<TutorialGroupApi> tutorialGroupApi) {
        this.ltiApi = ltiApi;
        this.tutorialGroupApi = tutorialGroupApi;
        this.authCheckService = authCheckService;
        this.fileService = fileService;
        this.tutorialGroupChannelManagementApi = tutorialGroupChannelManagementApi;
        this.learningPathApi = learningPathApi;
        this.autoOrchestrationApi = autoOrchestrationApi;
        this.conductAgreementService = conductAgreementService;
        this.learnerProfileApi = learnerProfileApi;
        this.courseRepository = courseRepository;
        this.courseConfigurationRepository = courseConfigurationRepository;
        this.courseAthenaConfigRepository = courseAthenaConfigRepository;
        this.userRepository = userRepository;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
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
    public ResponseEntity<CourseManagementDTO> updateCourse(@PathVariable Long courseId, @RequestPart("course") @Valid CourseUpdateDTO courseUpdateDTO,
            @RequestPart(required = false) MultipartFile file) {
        log.debug("REST request to update Course : {}", courseUpdateDTO);
        User user = userRepository.getUserWithAuthorities();

        // Always use the path variable for lookups to prevent a DTO with a mismatched id
        // from loading (and potentially modifying) a different course than the URL indicates
        var existingCourse = courseRepository.findByIdForUpdateElseThrow(courseId);

        // only allow admins or instructors of the existing course to change it
        // this is important, otherwise someone could put themselves into the instructor group of the updated course
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, existingCourse, user);

        // Attach the (lazily-stored) course configuration so applyTo can update its permanent row,
        // and so the admin-only auto-orchestration change detection below compares against the persisted values. Fetched
        // via its own repository to keep the course update entity graph small.
        existingCourse
                .setCourseConfiguration(courseConfigurationRepository.findByCourseId(courseId).orElseThrow(() -> new EntityNotFoundException("CourseConfiguration", courseId)));

        if (existingCourse.getTimeZone() != null && courseUpdateDTO.timeZone() == null) {
            throw new IllegalArgumentException("You can not remove the time zone of a course");
        }

        var timeZoneChanged = (existingCourse.getTimeZone() != null && courseUpdateDTO.timeZone() != null && !existingCourse.getTimeZone().equals(courseUpdateDTO.timeZone()));
        // Only a new or changed time zone is checked, so a course stored with one the server no longer knows stays editable.
        if (!Objects.equals(existingCourse.getTimeZone(), courseUpdateDTO.timeZone())) {
            CourseValidator.validateTimeZone(courseUpdateDTO.timeZone());
        }

        if (!Objects.equals(existingCourse.getShortName(), courseUpdateDTO.shortName())) {
            throw new BadRequestAlertException("The course short name cannot be changed", Course.ENTITY_NAME, "shortNameCannotChange", true);
        }

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
        CourseValidator.validateSemester(existingCourse);
        CourseValidator.validateEnrollmentStartAndEndDate(existingCourse);
        CourseValidator.validateUnenrollmentEndDate(existingCourse);
        if (file != null) {
            Path basePath = FilePathConverter.getCourseIconFilePath();
            Path savePath = FileUtil.saveFile(file, basePath, FilePathType.COURSE_ICON, false);
            existingCourse.setCourseIcon(savePath.getFileName().toString());
            if (existingCourseIcon != null) {
                // delete old course icon
                fileService.schedulePathForDeletion(new FileSystemLocation.CourseIcon(existingCourseIcon).path(), 0);
            }
        }
        else if (courseUpdateDTO.courseIcon() == null && existingCourseIcon != null) {
            // delete old course icon
            fileService.schedulePathForDeletion(new FileSystemLocation.CourseIcon(existingCourseIcon).path(), 0);
        }

        if (!Objects.equals(courseUpdateDTO.courseInformationSharingMessagingCodeOfConduct(), oldCodeOfConduct)) {
            conductAgreementService.resetUsersAgreeToCodeOfConductInCourse(existingCourse);
        }

        // Configurations live for the lifetime of the course. Toggling online mode only changes the course flag.
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
            Set<User> students = userRepository.getStudentsWithAuthorities(courseWithCompetencies);
            learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfiles(courseWithCompetencies, students));
            learningPathApi.ifPresent(api -> api.generateLearningPaths(courseWithCompetencies));
        }

        if (timeZoneChanged && tutorialGroupChannelManagementApi.isPresent()) {
            tutorialGroupChannelManagementApi.get().onTimeZoneUpdate(result);
        }

        // The Athena configuration is lazy and not part of the update, so attach it for the response to report the stored
        // flags; otherwise the client would cache a course that claims Athena is off.
        courseAthenaConfigRepository.attachTo(result);
        OnlineCourseConfiguration onlineConfiguration = ltiApi.flatMap(api -> api.findOnlineCourseConfiguration(courseId)).orElse(null);
        TutorialGroupsConfiguration tutorialConfiguration = tutorialGroupApi.flatMap(api -> api.findConfigurationByCourseId(courseId)).orElse(null);
        return ResponseEntity.ok(CourseManagementDTO.of(result, onlineConfiguration, tutorialConfiguration));
    }

    /**
     * GET /time-zones : The time zones a course may use, which the course form offers and validates against. Browsers
     * know different lists, some without names such as {@code UTC} or {@code Europe/Kyiv}, so the server, which
     * interprets the course's time zone, provides its own.
     *
     * @return the ResponseEntity with status 200 (OK) and the sorted time zone names
     */
    @GetMapping("time-zones")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<String>> getSupportedTimeZones() {
        return ResponseEntity.ok(DateUtil.SUPPORTED_TIME_ZONES);
    }
}
