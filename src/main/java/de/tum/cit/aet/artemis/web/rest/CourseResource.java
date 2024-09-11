package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.ZonedDateTime.now;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentDashboardService;
import de.tum.cit.aet.artemis.assessment.service.ComplaintService;
import de.tum.cit.aet.artemis.assessment.service.CourseScoreCalculationService;
import de.tum.cit.aet.artemis.assessment.service.GradingScaleService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.connectors.athena.AthenaModuleService;
import de.tum.cit.aet.artemis.core.service.connectors.ci.CIUserManagementService;
import de.tum.cit.aet.artemis.core.service.connectors.vcs.VcsUserManagementService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.service.ConductAgreementService;
import de.tum.cit.aet.artemis.service.CourseService;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.OnlineCourseConfigurationService;
import de.tum.cit.aet.artemis.service.SubmissionService;
import de.tum.cit.aet.artemis.service.dto.StudentDTO;
import de.tum.cit.aet.artemis.service.dto.UserDTO;
import de.tum.cit.aet.artemis.service.dto.UserPublicInfoDTO;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupsConfigurationService;
import de.tum.cit.aet.artemis.web.rest.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.web.rest.dto.CourseForImportDTO;
import de.tum.cit.aet.artemis.web.rest.dto.CourseManagementDetailViewDTO;
import de.tum.cit.aet.artemis.web.rest.dto.CourseManagementOverviewStatisticsDTO;
import de.tum.cit.aet.artemis.web.rest.dto.CoursesForDashboardDTO;
import de.tum.cit.aet.artemis.web.rest.dto.OnlineCourseDTO;
import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.web.rest.dto.user.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.cit.aet.artemis.web.rest.errors.ErrorConstants;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Course.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class CourseResource {

    private static final String ENTITY_NAME = "course";

    private static final String COMPLAINT_ENTITY_NAME = "complaint";

    private static final Logger log = LoggerFactory.getLogger(CourseResource.class);

    private static final int MAX_TITLE_LENGTH = 255;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<OnlineCourseConfigurationService> onlineCourseConfigurationService;

    private final CourseRepository courseRepository;

    private final ExerciseService exerciseService;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final SubmissionService submissionService;

    private final AssessmentDashboardService assessmentDashboardService;

    private final Optional<VcsUserManagementService> optionalVcsUserManagementService;

    private final Optional<CIUserManagementService> optionalCiUserManagementService;

    private final ExerciseRepository exerciseRepository;

    private final FileService fileService;

    private final TutorialGroupsConfigurationService tutorialGroupsConfigurationService;

    private final GradingScaleService gradingScaleService;

    private final CourseScoreCalculationService courseScoreCalculationService;

    private final GradingScaleRepository gradingScaleRepository;

    private final ConductAgreementService conductAgreementService;

    private final Optional<AthenaModuleService> athenaModuleService;

    @Value("${artemis.course-archives-path}")
    private String courseArchivesDirPath;

    private final LearningPathService learningPathService;

    private final ExamRepository examRepository;

    private final ComplaintService complaintService;

    private final TeamRepository teamRepository;

    public CourseResource(UserRepository userRepository, CourseService courseService, CourseRepository courseRepository, ExerciseService exerciseService,
            Optional<OnlineCourseConfigurationService> onlineCourseConfigurationService, AuthorizationCheckService authCheckService,
            TutorParticipationRepository tutorParticipationRepository, SubmissionService submissionService, Optional<VcsUserManagementService> optionalVcsUserManagementService,
            AssessmentDashboardService assessmentDashboardService, ExerciseRepository exerciseRepository, Optional<CIUserManagementService> optionalCiUserManagementService,
            FileService fileService, TutorialGroupsConfigurationService tutorialGroupsConfigurationService, GradingScaleService gradingScaleService,
            CourseScoreCalculationService courseScoreCalculationService, GradingScaleRepository gradingScaleRepository, LearningPathService learningPathService,
            ConductAgreementService conductAgreementService, Optional<AthenaModuleService> athenaModuleService, ExamRepository examRepository, ComplaintService complaintService,
            TeamRepository teamRepository) {
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.authCheckService = authCheckService;
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.submissionService = submissionService;
        this.optionalVcsUserManagementService = optionalVcsUserManagementService;
        this.optionalCiUserManagementService = optionalCiUserManagementService;
        this.assessmentDashboardService = assessmentDashboardService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.fileService = fileService;
        this.tutorialGroupsConfigurationService = tutorialGroupsConfigurationService;
        this.gradingScaleService = gradingScaleService;
        this.courseScoreCalculationService = courseScoreCalculationService;
        this.gradingScaleRepository = gradingScaleRepository;
        this.learningPathService = learningPathService;
        this.conductAgreementService = conductAgreementService;
        this.athenaModuleService = athenaModuleService;
        this.examRepository = examRepository;
        this.complaintService = complaintService;
        this.teamRepository = teamRepository;
    }

    /**
     * PUT /courses/:courseId : Updates an existing updatedCourse.
     *
     * @param courseId     the id of the course to update
     * @param courseUpdate the course to update
     * @param file         the optional course icon file
     * @return the ResponseEntity with status 200 (OK) and with body the updated course
     */
    @PutMapping(value = "courses/{courseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastInstructor
    public ResponseEntity<Course> updateCourse(@PathVariable Long courseId, @RequestPart("course") Course courseUpdate, @RequestPart(required = false) MultipartFile file)
            throws URISyntaxException {
        log.debug("REST request to update Course : {}", courseUpdate);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        var existingCourse = courseRepository.findByIdForUpdateElseThrow(courseUpdate.getId());

        if (existingCourse.getTimeZone() != null && courseUpdate.getTimeZone() == null) {
            throw new IllegalArgumentException("You can not remove the time zone of a course");
        }

        var timeZoneChanged = (existingCourse.getTimeZone() != null && courseUpdate.getTimeZone() != null && !existingCourse.getTimeZone().equals(courseUpdate.getTimeZone()));

        var athenaModuleAccessChanged = existingCourse.getRestrictedAthenaModulesAccess() != courseUpdate.getRestrictedAthenaModulesAccess();

        if (!Objects.equals(existingCourse.getShortName(), courseUpdate.getShortName())) {
            throw new BadRequestAlertException("The course short name cannot be changed", Course.ENTITY_NAME, "shortNameCannotChange", true);
        }

        // only allow admins or instructors of the existing course to change it
        // this is important, otherwise someone could put himself into the instructor group of the updated course
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, existingCourse, user);

        Set<String> existingGroupNames = new HashSet<>(List.of(existingCourse.getStudentGroupName(), existingCourse.getTeachingAssistantGroupName(),
                existingCourse.getEditorGroupName(), existingCourse.getInstructorGroupName()));
        Set<String> newGroupNames = new HashSet<>(List.of(courseUpdate.getStudentGroupName(), courseUpdate.getTeachingAssistantGroupName(), courseUpdate.getEditorGroupName(),
                courseUpdate.getInstructorGroupName()));
        Set<String> changedGroupNames = new HashSet<>(newGroupNames);
        changedGroupNames.removeAll(existingGroupNames);

        if (!authCheckService.isAdmin(user)) {
            // this means the user must be an instructor, who has NO Admin rights.
            // instructors are not allowed to change group names, because this would lead to security problems
            if (!changedGroupNames.isEmpty()) {
                throw new BadRequestAlertException("You are not allowed to change the group names of a course", Course.ENTITY_NAME, "groupNamesCannotChange", true);
            }
            // instructors are not allowed to change the access to restricted Athena modules
            if (athenaModuleAccessChanged) {
                throw new BadRequestAlertException("You are not allowed to change the access to restricted Athena modules of a course", Course.ENTITY_NAME,
                        "restrictedAthenaModulesAccessCannotChange", true);
            }
            // instructors are not allowed to change the dashboard settings
            if (existingCourse.getStudentCourseAnalyticsDashboardEnabled() != courseUpdate.getStudentCourseAnalyticsDashboardEnabled()) {
                throw new BadRequestAlertException("You are not allowed to change the dashboard settings of a course", Course.ENTITY_NAME, "dashboardSettingsCannotChange", true);
            }
        }

        if (courseUpdate.getPresentationScore() != null && courseUpdate.getPresentationScore() != 0) {
            Optional<GradingScale> gradingScale = gradingScaleService.findGradingScaleByCourseId(courseUpdate.getId());
            if (gradingScale.isPresent() && gradingScale.get().getPresentationsNumber() != null) {
                throw new BadRequestAlertException("You cannot set a presentation score if the grading scale is already set up for graded presentations", Course.ENTITY_NAME,
                        "gradedPresentationAlreadySet", true);
            }
            if (courseUpdate.getPresentationScore() < 0) {
                throw new BadRequestAlertException("The presentation score cannot be negative", Course.ENTITY_NAME, "negativePresentationScore", true);
            }
        }

        // Make sure to preserve associations in updated entity
        courseUpdate.setId(courseId);
        courseUpdate.setPrerequisites(existingCourse.getPrerequisites());
        courseUpdate.setTutorialGroupsConfiguration(existingCourse.getTutorialGroupsConfiguration());
        courseUpdate.setOnlineCourseConfiguration(existingCourse.getOnlineCourseConfiguration());

        if (courseUpdate.getTitle().length() > MAX_TITLE_LENGTH) {
            throw new BadRequestAlertException("The course title is too long", Course.ENTITY_NAME, "courseTitleTooLong");
        }

        courseUpdate.validateEnrollmentConfirmationMessage();
        courseUpdate.validateComplaintsAndRequestMoreFeedbackConfig();
        courseUpdate.validateOnlineCourseAndEnrollmentEnabled();
        courseUpdate.validateShortName();
        courseUpdate.validateAccuracyOfScores();
        courseUpdate.validateStartAndEndDate();
        courseUpdate.validateEnrollmentStartAndEndDate();
        courseUpdate.validateUnenrollmentEndDate();

        if (file != null) {
            Path basePath = FilePathService.getCourseIconFilePath();
            Path savePath = fileService.saveFile(file, basePath, false);
            courseUpdate.setCourseIcon(FilePathService.publicPathForActualPathOrThrow(savePath, courseId).toString());
            if (existingCourse.getCourseIcon() != null) {
                // delete old course icon
                fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(new URI(existingCourse.getCourseIcon())), 0);
            }
        }
        else if (courseUpdate.getCourseIcon() == null && existingCourse.getCourseIcon() != null) {
            // delete old course icon
            fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(new URI(existingCourse.getCourseIcon())), 0);
        }

        if (courseUpdate.isOnlineCourse() != existingCourse.isOnlineCourse()) {
            if (courseUpdate.isOnlineCourse() && onlineCourseConfigurationService.isPresent()) {
                onlineCourseConfigurationService.get().createOnlineCourseConfiguration(courseUpdate);
            }
            else {
                courseUpdate.setOnlineCourseConfiguration(null);
            }
        }

        if (!Objects.equals(courseUpdate.getCourseInformationSharingMessagingCodeOfConduct(), existingCourse.getCourseInformationSharingMessagingCodeOfConduct())) {
            conductAgreementService.resetUsersAgreeToCodeOfConductInCourse(existingCourse);
        }

        courseUpdate.setId(courseId); // Don't persist a wrong ID
        Course result = courseRepository.save(courseUpdate);

        // if learning paths got enabled, generate learning paths for students
        if (existingCourse.getLearningPathsEnabled() != courseUpdate.getLearningPathsEnabled() && courseUpdate.getLearningPathsEnabled()) {
            Course courseWithCompetencies = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(result.getId());
            learningPathService.generateLearningPaths(courseWithCompetencies);
        }

        // if access to restricted athena modules got disabled for the course, we need to set all exercises that use restricted modules to null
        if (athenaModuleAccessChanged && !courseUpdate.getRestrictedAthenaModulesAccess()) {
            athenaModuleService.ifPresent(ams -> ams.revokeAccessToRestrictedFeedbackSuggestionModules(result));
        }

        // Based on the old instructors, editors and TAs, we can update all exercises in the course in the VCS (if necessary)
        // We need the old instructors, editors and TAs, so that the VCS user management service can determine which
        // users no longer have TA, editor or instructor rights in the related exercise repositories.
        final var oldInstructorGroup = existingCourse.getInstructorGroupName();
        final var oldEditorGroup = existingCourse.getEditorGroupName();
        final var oldTeachingAssistantGroup = existingCourse.getTeachingAssistantGroupName();

        optionalVcsUserManagementService
                .ifPresent(userManagementService -> userManagementService.updateCoursePermissions(result, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup));
        optionalCiUserManagementService
                .ifPresent(ciUserManagementService -> ciUserManagementService.updateCoursePermissions(result, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup));
        if (timeZoneChanged) {
            tutorialGroupsConfigurationService.onTimeZoneUpdate(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * PUT courses/:courseId/onlineCourseConfiguration : Updates the onlineCourseConfiguration for the given course.
     *
     * @param courseId                  the id of the course to update
     * @param onlineCourseConfiguration the online course configuration to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated online course configuration
     */
    // TODO: move into LTIResource
    @PutMapping("courses/{courseId}/onlineCourseConfiguration")
    @EnforceAtLeastInstructor
    @Profile("lti")
    public ResponseEntity<OnlineCourseConfiguration> updateOnlineCourseConfiguration(@PathVariable Long courseId,
            @RequestBody OnlineCourseConfiguration onlineCourseConfiguration) {
        log.debug("REST request to update the online course configuration for Course : {}", courseId);

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        if (!course.isOnlineCourse()) {
            throw new BadRequestAlertException("Course must be online course", Course.ENTITY_NAME, "courseMustBeOnline");
        }

        if (!course.getOnlineCourseConfiguration().getId().equals(onlineCourseConfiguration.getId())) {
            throw new BadRequestAlertException("The onlineCourseConfigurationId does not match the id of the course's onlineCourseConfiguration",
                    OnlineCourseConfiguration.ENTITY_NAME, "idMismatch");
        }

        if (onlineCourseConfigurationService.isPresent()) {
            onlineCourseConfigurationService.get().validateOnlineCourseConfiguration(onlineCourseConfiguration);
            course.setOnlineCourseConfiguration(onlineCourseConfiguration);
            try {
                onlineCourseConfigurationService.get().addOnlineCourseConfigurationToLtiConfigurations(onlineCourseConfiguration);
            }
            catch (Exception ex) {
                log.error("Failed to add online course configuration to LTI configurations", ex);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error when adding online course configuration to LTI configurations", ex);
            }
        }

        courseRepository.save(course);

        return ResponseEntity.ok(onlineCourseConfiguration);
    }

    /**
     * GET courses/for-lti-dashboard : Retrieves a list of online courses for a specific LTI dashboard based on the client ID.
     *
     * @param clientId the client ID of the LTI platform used to filter the courses.
     * @return a {@link ResponseEntity} containing a list of {@link OnlineCourseDTO} for the courses the user has access to.
     */
    @GetMapping("courses/for-lti-dashboard")
    @EnforceAtLeastInstructor
    @Profile("lti")
    public ResponseEntity<List<OnlineCourseDTO>> findAllOnlineCoursesForLtiDashboard(@RequestParam("clientId") String clientId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get all online courses the user {} has access to", user.getLogin());

        Set<Course> courses = courseService.findAllOnlineCoursesForPlatformForUser(clientId, user);

        List<OnlineCourseDTO> onlineCourseDTOS = courses.stream()
                .map(c -> new OnlineCourseDTO(c.getId(), c.getTitle(), c.getShortName(), c.getOnlineCourseConfiguration().getLtiPlatformConfiguration().getRegistrationId()))
                .toList();

        return ResponseEntity.ok(onlineCourseDTOS);
    }

    /**
     * POST /courses/{courseId}/enroll : Enroll in an existing course. This method enrolls the current user for the given course id in case the course has already started
     * and not finished yet. The user is added to the course student group in the Authentication System and the course student group is added to the user's groups in the Artemis
     * database.
     *
     * @param courseId to find the course
     * @return response entity for groups of user who has been enrolled in the course
     */
    @PostMapping("courses/{courseId}/enroll")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<String>> enrollInCourse(@PathVariable Long courseId) {
        User user = userRepository.getUserWithGroupsAndAuthoritiesAndOrganizations();
        Course course = courseRepository.findWithEagerOrganizationsAndCompetenciesAndPrerequisitesAndLearningPathsElseThrow(courseId);
        log.debug("REST request to enroll {} in Course {}", user.getName(), course.getTitle());
        courseService.enrollUserForCourseOrThrow(user, course);
        return ResponseEntity.ok(user.getGroups());
    }

    /**
     * POST /courses/{courseId}/unenroll : Unenroll from an existing course. This method unenrolls the current user for the given course id in case the student is currently
     * enrolled.
     * The user is removed from the course student group in the Authentication System and the course student group is removed from the user's groups in the Artemis
     * database.
     *
     * @param courseId to find the course
     * @return response entity for groups of user who has been unenrolled from the course
     */
    @PostMapping("courses/{courseId}/unenroll")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<String>> unenrollFromCourse(@PathVariable Long courseId) {
        Course course = courseRepository.findWithEagerOrganizationsElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthoritiesAndOrganizations();
        log.debug("REST request to unenroll {} for Course {}", user.getName(), course.getTitle());
        courseService.unenrollUserForCourseOrThrow(user, course);
        return ResponseEntity.ok(user.getGroups());
    }

    /**
     * GET /courses : get all courses for administration purposes.
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return the ResponseEntity with status 200 (OK) and with body the list of courses (the user has access to)
     */
    @GetMapping("courses")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Course>> getCourses(@RequestParam(defaultValue = "false") boolean onlyActive) {
        log.debug("REST request to get all Courses the user has access to");
        User user = userRepository.getUserWithGroupsAndAuthorities();
        // TODO: we should avoid findAll() and instead try to filter this directly in the database, in case of admins, we should load batches of courses, e.g. per semester
        List<Course> courses = courseRepository.findAll();
        Stream<Course> userCourses = courses.stream().filter(course -> user.getGroups().contains(course.getTeachingAssistantGroupName())
                || user.getGroups().contains(course.getInstructorGroupName()) || authCheckService.isAdmin(user));
        if (onlyActive) {
            // only include courses that have NOT been finished
            userCourses = userCourses.filter(course -> course.getEndDate() == null || course.getEndDate().isAfter(ZonedDateTime.now()));
        }
        return ResponseEntity.ok(userCourses.toList());
    }

    /**
     * GET /courses/for-import : Get a list of {@link CourseForImportDTO CourseForImportDTOs} where the user is instructor/editor. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return the ResponseEntity with status 200 (OK) and with body the desired page
     */
    @GetMapping("courses/for-import")
    @EnforceAtLeastInstructor
    public ResponseEntity<SearchResultPageDTO<CourseForImportDTO>> getCoursesForImport(SearchTermPageableSearchDTO<String> search) {
        log.debug("REST request to get a list of courses for import.");
        User user = userRepository.getUserWithGroupsAndAuthorities();
        var coursePage = courseService.getAllOnPageWithSize(search, user);
        var resultsOnPage = coursePage.getResultsOnPage().stream().map(CourseForImportDTO::new).toList();
        return ResponseEntity.ok(new SearchResultPageDTO<>(resultsOnPage, coursePage.getNumberOfPages()));
    }

    /**
     * GET /courses/courses-with-quiz : get all courses with quiz exercises for administration purposes.
     *
     * @return the ResponseEntity with status 200 (OK) and with body the list of courses
     */
    @GetMapping("courses/courses-with-quiz")
    @EnforceAtLeastEditor
    public ResponseEntity<List<Course>> getCoursesWithQuizExercises() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (authCheckService.isAdmin(user)) {
            return ResponseEntity.ok(courseRepository.findAllWithQuizExercisesWithEagerExercises());
        }
        else {
            var userGroups = new ArrayList<>(user.getGroups());
            return ResponseEntity.ok(courseRepository.getCoursesWithQuizExercisesForWhichUserHasAtLeastEditorAccess(userGroups));
        }
    }

    /**
     * GET /courses/with-user-stats : get all courses for administration purposes with user stats.
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return the ResponseEntity with status 200 (OK) and with body the list of courses (the user has access to)
     */
    @GetMapping("courses/with-user-stats")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Course>> getCoursesWithUserStats(@RequestParam(defaultValue = "false") boolean onlyActive) {
        log.debug("get courses with user stats, only active: {}", onlyActive);
        // TODO: we should avoid using an endpoint in such cases and instead call a service method
        List<Course> courses = getCourses(onlyActive).getBody();
        for (Course course : courses) {
            course.setNumberOfInstructors(userRepository.countUserInGroup(course.getInstructorGroupName()));
            course.setNumberOfTeachingAssistants(userRepository.countUserInGroup(course.getTeachingAssistantGroupName()));
            course.setNumberOfEditors(userRepository.countUserInGroup(course.getEditorGroupName()));
            course.setNumberOfStudents(userRepository.countUserInGroup(course.getStudentGroupName()));
        }
        return ResponseEntity.ok(courses);
    }

    /**
     * GET /courses/course-overview : get all courses for the management overview
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return the ResponseEntity with status 200 (OK) and with body a list of courses (the user has access to)
     */
    @GetMapping("courses/course-management-overview")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Course>> getCoursesForManagementOverview(@RequestParam(defaultValue = "false") boolean onlyActive) {
        return ResponseEntity.ok(courseService.getAllCoursesForManagementOverview(onlyActive));
    }

    /**
     * GET /courses/{courseId}/for-enrollment : get a course by id if the course allows enrollment and is currently active.
     *
     * @param courseId the id of the course to retrieve
     * @return the active course
     */
    @GetMapping("courses/{courseId}/for-enrollment")
    @EnforceAtLeastStudent
    public ResponseEntity<Course> getCourseForEnrollment(@PathVariable long courseId) {
        log.debug("REST request to get a currently active course for enrollment");
        User user = userRepository.getUserWithGroupsAndAuthoritiesAndOrganizations();

        Course course = courseRepository.findSingleWithOrganizationsAndPrerequisitesElseThrow(courseId);
        authCheckService.checkUserAllowedToEnrollInCourseElseThrow(user, course);

        return ResponseEntity.ok(course);
    }

    /**
     * GET /courses/for-enrollment : get all courses that the current user can enroll in.
     * Decided by the start and end date and if the enrollmentEnabled flag is set correctly
     *
     * @return the ResponseEntity with status 200 (OK) and with body the list of courses which are active
     */
    @GetMapping("courses/for-enrollment")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Course>> getCoursesForEnrollment() {
        log.debug("REST request to get all currently active courses that are not online courses");
        User user = userRepository.getUserWithGroupsAndAuthoritiesAndOrganizations();
        final var courses = courseService.findAllEnrollableForUser(user).stream().filter(course -> authCheckService.isUserAllowedToSelfEnrollInCourse(user, course)).toList();
        return ResponseEntity.ok(courses);
    }

    /**
     * GET /courses/{courseId}/for-dashboard
     *
     * @param courseId the courseId for which exercises, lectures, exams and competencies should be fetched
     * @return a DTO containing a course with all exercises, lectures, exams, competencies, etc. visible to the user as well as the total scores for the course, the scores per
     *         exercise type for each exercise, and the participation result for each participation.
     */
    // TODO: we should rename this into courses/{courseId}/details
    @GetMapping("courses/{courseId}/for-dashboard")
    @EnforceAtLeastStudent
    public ResponseEntity<CourseForDashboardDTO> getCourseForDashboard(@PathVariable long courseId) {
        long timeNanoStart = System.nanoTime();
        log.debug("REST request to get one course {} with exams, lectures, exercises, participations, submissions and results, etc.", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        Course course = courseService.findOneWithExercisesAndLecturesAndExamsAndCompetenciesAndTutorialGroupsForUser(courseId, user);
        log.debug("courseService.findOneWithExercisesAndLecturesAndExamsAndCompetenciesAndTutorialGroupsForUser done");
        if (!authCheckService.isAtLeastStudentInCourse(course, user)) {
            // user might be allowed to enroll in the course
            // We need the course with organizations so that we can check if the user is allowed to enroll
            course = courseRepository.findSingleWithOrganizationsAndPrerequisitesElseThrow(courseId);
            if (authCheckService.isUserAllowedToSelfEnrollInCourse(user, course)) {
                // suppress error alert with skipAlert: true so that the client can redirect to the enrollment page
                throw new AccessForbiddenAlertException(ErrorConstants.DEFAULT_TYPE, "You don't have access to this course, but you could enroll in it.", ENTITY_NAME,
                        "noAccessButCouldEnroll", true);
            }
            else {
                // user is not even allowed to self-enroll
                // just normally throw the access forbidden exception
                throw new AccessForbiddenException(ENTITY_NAME, courseId);
            }
        }

        courseService.fetchParticipationsWithSubmissionsAndResultsForCourses(List.of(course), user, true);
        log.debug("courseService.fetchParticipationsWithSubmissionsAndResultsForCourses done");
        courseService.fetchPlagiarismCasesForCourseExercises(course.getExercises(), user.getId());
        log.debug("courseService.fetchPlagiarismCasesForCourseExercises done");
        GradingScale gradingScale = gradingScaleRepository.findByCourseId(course.getId()).orElse(null);
        log.debug("gradingScaleRepository.findByCourseId done");
        CourseForDashboardDTO courseForDashboardDTO = courseScoreCalculationService.getScoresAndParticipationResults(course, gradingScale, user.getId());
        logDuration(List.of(course), user, timeNanoStart, "courses/" + courseId + "/for-dashboard (single course)");
        return ResponseEntity.ok(courseForDashboardDTO);
    }

    /**
     * GET /courses/for-dropdown
     *
     * @return contains all courses the user has access to with id, title and icon
     */
    @GetMapping("courses/for-dropdown")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<CourseDropdownDTO>> getCoursesForDropdown() {
        long start = System.nanoTime();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        final var courses = courseService.findAllActiveForUser(user);
        final var response = courses.stream().map(course -> new CourseDropdownDTO(course.getId(), course.getTitle(), course.getCourseIcon())).collect(Collectors.toSet());
        log.info("GET /courses/for-dropdown took {} for {} courses for user {}", TimeLogUtil.formatDurationFrom(start), courses.size(), user.getLogin());
        return ResponseEntity.ok(response);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseDropdownDTO(Long id, String title, String courseIcon) {
    }

    /**
     * GET /courses/for-dashboard
     *
     * @return the ResponseEntity with status 200 (OK) and with body a DTO containing a list of courses (the user has access to) including all exercises with participation,
     *         submission and result, etc. for the user. In addition, the
     *         DTO contains the total scores for the course, the scores per exercise
     *         type for each exercise, and the participation result for each participation.
     */
    @GetMapping("courses/for-dashboard")
    @EnforceAtLeastStudent
    public ResponseEntity<CoursesForDashboardDTO> getCoursesForDashboard() {
        long timeNanoStart = System.nanoTime();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("Request to get all courses user {} has access to with exams, lectures, exercises, participations, submissions and results + calculated scores", user.getLogin());
        Set<Course> courses = courseService.findAllActiveWithExercisesForUser(user);
        log.debug("courseService.findAllActiveWithExercisesForUser done");
        courseService.fetchParticipationsWithSubmissionsAndResultsForCourses(courses, user, false);

        log.debug("courseService.fetchParticipationsWithSubmissionsAndResultsForCourses done");
        // TODO: we should avoid fetching plagiarism in the future, it's unnecessary for 99.9% of the cases
        courseService.fetchPlagiarismCasesForCourseExercises(courses.stream().flatMap(course -> course.getExercises().stream()).collect(Collectors.toSet()), user.getId());

        log.debug("courseService.fetchPlagiarismCasesForCourseExercises done");
        // TODO: loading the grading scale here is only done to handle edge cases, we should avoid it, it's unnecessary for 90% of the cases and can be done when navigating into
        // the course
        var gradingScales = gradingScaleRepository.findAllByCourseIds(courses.stream().map(Course::getId).collect(Collectors.toSet()));
        // we explicitly add 1 hour here to compensate for potential write extensions. Calculating it exactly is not feasible here
        var activeExams = examRepository.findActiveExams(courses.stream().map(Course::getId).collect(Collectors.toSet()), user.getId(), ZonedDateTime.now(),
                ZonedDateTime.now().plusHours(1));

        log.debug("gradingScaleRepository.findAllByCourseIds done");
        Set<CourseForDashboardDTO> coursesForDashboard = new HashSet<>();
        for (Course course : courses) {
            GradingScale gradingScale = gradingScales.stream().filter(scale -> scale.getCourse().getId().equals(course.getId())).findFirst().orElse(null);
            CourseForDashboardDTO courseForDashboardDTO = courseScoreCalculationService.getScoresAndParticipationResults(course, gradingScale, user.getId());
            coursesForDashboard.add(courseForDashboardDTO);
        }
        logDuration(courses, user, timeNanoStart, "courses/for-dashboard (multiple courses)");
        final var dto = new CoursesForDashboardDTO(coursesForDashboard, activeExams);
        return ResponseEntity.ok(dto);
    }

    private void logDuration(Collection<Course> courses, User user, long timeNanoStart, String path) {
        if (log.isInfoEnabled()) {
            Set<Exercise> exercises = courses.stream().flatMap(course -> course.getExercises().stream()).collect(Collectors.toSet());
            Map<ExerciseMode, List<Exercise>> exercisesGroupedByExerciseMode = exercises.stream().collect(Collectors.groupingBy(Exercise::getMode));
            int noOfIndividualExercises = Objects.requireNonNullElse(exercisesGroupedByExerciseMode.get(ExerciseMode.INDIVIDUAL), List.of()).size();
            int noOfTeamExercises = Objects.requireNonNullElse(exercisesGroupedByExerciseMode.get(ExerciseMode.TEAM), List.of()).size();
            log.info("{} finished in {} for {} courses with {} individual exercise(s) and {} team exercise(s) for user {}", path, TimeLogUtil.formatDurationFrom(timeNanoStart),
                    courses.size(), noOfIndividualExercises, noOfTeamExercises, user.getLogin());
        }
    }

    /**
     * GET /courses/for-notifications
     *
     * @return the ResponseEntity with status 200 (OK) and with body the set of courses (the user has access to)
     */
    @GetMapping("courses/for-notifications")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<Course>> getCoursesForNotifications() {
        log.debug("REST request to get all Courses the user has access to");
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(courseService.findAllActiveForUser(user));
    }

    /**
     * GET /courses/:courseId/for-assessment-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("courses/{courseId}/for-assessment-dashboard")
    @EnforceAtLeastTutor
    public ResponseEntity<Course> getCourseForAssessmentDashboard(@PathVariable long courseId) {
        log.debug("REST request /courses/{courseId}/for-assessment-dashboard");
        // TODO: use ...ElseThrow below in case the course cannot be found
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);

        Set<Exercise> interestingExercises = courseRepository.filterInterestingExercisesForAssessmentDashboards(course.getExercises());
        course.setExercises(interestingExercises);
        List<TutorParticipation> tutorParticipations = tutorParticipationRepository.findAllByAssessedExercise_Course_IdAndTutor_Id(course.getId(), user.getId());
        assessmentDashboardService.generateStatisticsForExercisesForAssessmentDashboard(course.getExercises(), tutorParticipations, false);
        return ResponseEntity.ok(course);
    }

    /**
     * GET /courses/:courseId/stats-for-assessment-dashboard A collection of useful statistics for the tutor course dashboard, including: - number of submissions to the course -
     * number of assessments - number of assessments assessed by the tutor - number of complaints
     * <p>
     * all timestamps were measured when calling this method from the PGdP assessment-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("courses/{courseId}/stats-for-assessment-dashboard")
    @EnforceAtLeastTutor
    public ResponseEntity<StatsForDashboardDTO> getStatsForAssessmentDashboard(@PathVariable long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        StatsForDashboardDTO stats = courseService.getStatsForDashboardDTO(course);
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /courses/:courseId : get the "id" course.
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Course> getCourse(@PathVariable Long courseId) {
        log.debug("REST request to get Course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        if (authCheckService.isAtLeastInstructorInCourse(course, user)) {
            course = courseRepository.findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(courseId);
        }
        else if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            course = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(courseId);
        }

        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            course.setNumberOfInstructors(userRepository.countUserInGroup(course.getInstructorGroupName()));
            course.setNumberOfTeachingAssistants(userRepository.countUserInGroup(course.getTeachingAssistantGroupName()));
            course.setNumberOfEditors(userRepository.countUserInGroup(course.getEditorGroupName()));
            course.setNumberOfStudents(userRepository.countUserInGroup(course.getStudentGroupName()));
        }

        return ResponseEntity.ok(course);
    }

    /**
     * GET /courses/:courseId : get the "id" course.
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/with-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<Course> getCourseWithExercises(@PathVariable Long courseId) {
        log.debug("REST request to get Course : {}", courseId);
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        return ResponseEntity.ok(course);
    }

    /**
     * GET /courses/:courseId/with-organizations Get a course by id with eagerly loaded organizations
     *
     * @param courseId the id of the course
     * @return the course with eagerly loaded organizations
     */
    @GetMapping("courses/{courseId}/with-organizations")
    @EnforceAtLeastTutor
    public ResponseEntity<Course> getCourseWithOrganizations(@PathVariable Long courseId) {
        log.debug("REST request to get a course with its organizations : {}", courseId);
        Course course = courseRepository.findWithEagerOrganizationsElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        return ResponseEntity.ok(course);
    }

    /**
     * GET /courses/:courseId/lockedSubmissions Get locked submissions for course for user
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/lockedSubmissions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Submission>> getLockedSubmissionsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all locked submissions for course : {}", courseId);
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);

        List<Submission> submissions = submissionService.getLockedSubmissions(courseId);
        for (Submission submission : submissions) {
            submissionService.hideDetails(submission, user);
        }

        return ResponseEntity.ok(submissions);
    }

    /**
     * GET /courses/exercises-for-management-overview
     * <p>
     * gets the courses with exercises for the user
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return ResponseEntity with status, containing a list of courses
     */
    @GetMapping("courses/exercises-for-management-overview")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Course>> getExercisesForCourseOverview(@RequestParam(defaultValue = "false") boolean onlyActive) {
        final List<Course> courses = new ArrayList<>();
        for (final var course : courseService.getAllCoursesForManagementOverview(onlyActive)) {
            course.setExercises(exerciseRepository.getExercisesForCourseManagementOverview(course.getId()));
            courses.add(course);
        }
        return ResponseEntity.ok(courses);
    }

    /**
     * GET /courses/stats-for-management-overview
     * <p>
     * gets the statistics for the courses of the user
     * statistics for exercises with an assessment due date (or due date if there is no assessment due date)
     * in the past are limited to the five most recent
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return ResponseEntity with status, containing a list of <code>CourseManagementOverviewStatisticsDTO</code>
     */
    @GetMapping("courses/stats-for-management-overview")
    @EnforceAtLeastTutor
    public ResponseEntity<List<CourseManagementOverviewStatisticsDTO>> getExerciseStatsForCourseOverview(@RequestParam(defaultValue = "false") boolean onlyActive) {
        log.debug("REST request to get statistics for the courses of the user");
        final List<CourseManagementOverviewStatisticsDTO> courseDTOs = new ArrayList<>();
        for (final var course : courseService.getAllCoursesForManagementOverview(onlyActive)) {
            final var courseId = course.getId();
            var studentsGroup = course.getStudentGroupName();
            var amountOfStudentsInCourse = Math.toIntExact(userRepository.countUserInGroup(studentsGroup));
            var exerciseStatistics = exerciseService.getStatisticsForCourseManagementOverview(courseId, amountOfStudentsInCourse);

            var exerciseIds = exerciseRepository.findAllIdsByCourseId(courseId);
            var endDate = courseService.determineEndDateForActiveStudents(course);
            var timeSpanSize = courseService.determineTimeSpanSizeForActiveStudents(course, endDate, 4);
            var activeStudents = courseService.getActiveStudents(exerciseIds, 0, timeSpanSize, endDate);

            final var courseDTO = new CourseManagementOverviewStatisticsDTO(courseId, activeStudents, exerciseStatistics);
            courseDTOs.add(courseDTO);
        }

        return ResponseEntity.ok(courseDTOs);
    }

    /**
     * PUT /courses/{courseId} : archive an existing course asynchronously. This method starts the process of archiving all course exercises, submissions and results in a large
     * zip file. It immediately returns and runs this task asynchronously. When the task is done, the course is marked as archived, which means the zip file can be downloaded.
     *
     * @param courseId the id of the course
     * @return empty
     */
    @PutMapping("courses/{courseId}/archive")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Void> archiveCourse(@PathVariable Long courseId) {
        log.info("REST request to archive Course : {}", courseId);
        final Course course = courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        // Archiving a course is only possible after the course is over
        if (now().isBefore(course.getEndDate())) {
            throw new BadRequestAlertException("You cannot archive a course that is not over.", Course.ENTITY_NAME, "courseNotOver", true);
        }
        courseService.archiveCourse(course);

        // Note: in the first version, we do not store the results with feedback and other metadata, as those will stay available in Artemis, the main focus is to allow
        // instructors to download student repos in order to delete those on Gitlab

        // Note: Lectures are not part of the archive at the moment and will be included in a future version
        // 1) Get all lectures (attachments) of the course and store them in a folder

        // Note: Questions and answers are not part of the archive at the moment and will be included in a future version
        // 1) Get all questions and answers for exercises and lectures and store those in structured text files

        return ResponseEntity.ok().build();
    }

    /**
     * Downloads the zip file of the archived course if it exists. Throws a 404 if the course doesn't exist
     *
     * @param courseId The course id of the archived course
     * @return ResponseEntity with status
     */
    @EnforceAtLeastInstructor
    @GetMapping("courses/{courseId}/download-archive")
    public ResponseEntity<Resource> downloadCourseArchive(@PathVariable Long courseId) throws FileNotFoundException {
        log.info("REST request to download archive of Course : {}", courseId);
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        if (!course.hasCourseArchive()) {
            throw new EntityNotFoundException("Archived course", courseId);
        }

        // The path is stored in the course table
        Path archive = Path.of(courseArchivesDirPath, course.getCourseArchivePath());

        File zipFile = archive.toFile();
        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment").filename(zipFile.getName()).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        return ResponseEntity.ok().headers(headers).contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName())
                .body(resource);
    }

    /**
     * DELETE /courses/:course/cleanup : Cleans up a course by deleting all student submissions.
     *
     * @param courseId  id of the course to clean up
     * @param principal the user that wants to cleanup the course
     * @return ResponseEntity with status
     */
    @DeleteMapping("courses/{courseId}/cleanup")
    @EnforceAtLeastInstructor
    public ResponseEntity<Resource> cleanup(@PathVariable Long courseId, Principal principal) {
        log.info("REST request to cleanup the Course : {}", courseId);
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        // Forbid cleaning the course if no archive has been created
        if (!course.hasCourseArchive()) {
            throw new BadRequestAlertException("Failed to clean up course " + courseId + " because it needs to be archived first.", Course.ENTITY_NAME, "archivenonexistant");
        }
        courseService.cleanupCourse(courseId, principal);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/:courseId/categories : Returns all categories used in a course
     *
     * @param courseId the id of the course to get the categories from
     * @return the ResponseEntity with status 200 (OK) and the list of categories or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/categories")
    @EnforceAtLeastEditor
    public ResponseEntity<Set<String>> getCategoriesInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get categories of Course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        return ResponseEntity.ok().body(exerciseRepository.findAllCategoryNames(course.getId()));
    }

    /**
     * GET /courses/:courseId/students : Returns all users that belong to the student group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/students")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<User>> getStudentsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all students in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return courseService.getAllUsersInGroup(course, course.getStudentGroupName());
    }

    /**
     * GET /courses/:courseId/students/search : Search all users by login or name that belong to the student group of the course
     *
     * @param courseId    the id of the course
     * @param loginOrName the login or name by which to search users
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("courses/{courseId}/students/search")
    @EnforceAtLeastTutor
    public ResponseEntity<List<UserDTO>> searchStudentsInCourse(@PathVariable Long courseId, @RequestParam("loginOrName") String loginOrName) {
        log.debug("REST request to search for students in course : {} with login or name : {}", courseId, loginOrName);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        // restrict result size by only allowing reasonable searches
        if (loginOrName.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'loginOrName' must be three characters or longer.");
        }
        final Page<UserDTO> page = userRepository.searchAllUsersByLoginOrNameInGroupAndConvertToDTO(PageRequest.of(0, 25), loginOrName, course.getStudentGroupName());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /courses/:courseId/users/search : Search all users by login or name that belong to the specified groups of the course
     *
     * @param courseId    the id of the course
     * @param loginOrName the login or name by which to search users
     * @param roles       the roles which should be searched in
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("courses/{courseId}/users/search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<UserPublicInfoDTO>> searchUsersInCourse(@PathVariable Long courseId, @RequestParam("loginOrName") String loginOrName,
            @RequestParam("roles") List<String> roles) {
        log.debug("REST request to search users in course : {} with login or name : {}", courseId, loginOrName);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        var requestedRoles = roles.stream().map(Role::fromString).collect(Collectors.toSet());
        // restrict result size by only allowing reasonable searches if student role is selected
        if (loginOrName.length() < 3 && requestedRoles.contains(Role.STUDENT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'loginOrName' must be three characters or longer if you search for students.");
        }
        var groups = new HashSet<String>();
        if (requestedRoles.contains(Role.STUDENT)) {
            groups.add(course.getStudentGroupName());
        }
        if (requestedRoles.contains(Role.TEACHING_ASSISTANT)) {
            groups.add(course.getTeachingAssistantGroupName());
            // searching for tutors also searches for editors
            groups.add(course.getEditorGroupName());
        }
        if (requestedRoles.contains(Role.INSTRUCTOR)) {
            groups.add(course.getInstructorGroupName());
        }
        User searchingUser = userRepository.getUser();
        var originalPage = userRepository.searchAllWithGroupsByLoginOrNameInGroupsNotUserId(PageRequest.of(0, 25), loginOrName, groups, searchingUser.getId());

        var resultDTOs = new ArrayList<UserPublicInfoDTO>();
        for (var user : originalPage) {
            var dto = new UserPublicInfoDTO(user);
            UserPublicInfoDTO.assignRoleProperties(course, user, dto);
            if (!resultDTOs.contains(dto)) {
                resultDTOs.add(dto);
            }
        }
        var dtoPage = new PageImpl<>(resultDTOs, originalPage.getPageable(), originalPage.getTotalElements());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), dtoPage);
        return new ResponseEntity<>(dtoPage.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /courses/:courseId/tutors : Returns all users that belong to the tutor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/tutors")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<User>> getTutorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all tutors in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return courseService.getAllUsersInGroup(course, course.getTeachingAssistantGroupName());
    }

    /**
     * GET /courses/:courseId/editors : Returns all users that belong to the editor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/editors")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<User>> getEditorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all editors in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return courseService.getAllUsersInGroup(course, course.getEditorGroupName());
    }

    /**
     * GET /courses/:courseId/instructors : Returns all users that belong to the instructor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/instructors")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<User>> getInstructorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all instructors in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return courseService.getAllUsersInGroup(course, course.getInstructorGroupName());
    }

    /**
     * GET /courses/:courseId/search-users : search users for a given course within all groups.
     *
     * @param courseId   the id of the course for which to search users
     * @param nameOfUser the name by which to search users
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("courses/{courseId}/search-other-users")
    @EnforceAtLeastStudent
    public ResponseEntity<List<User>> searchOtherUsersInCourse(@PathVariable long courseId, @RequestParam("nameOfUser") String nameOfUser) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        // restrict result size by only allowing reasonable searches
        if (nameOfUser.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'name' must be three characters or longer.");
        }

        return ResponseEntity.ok().body(courseService.searchOtherUsersNameInCourse(course, nameOfUser));
    }

    /**
     * GET /api/courses/:courseId/members/search: Searches for members of a course
     *
     * @param courseId    id of the course
     * @param loginOrName the search term to search login and names by
     * @return the ResponseEntity with status 200 (OK) and with body containing the list of found members matching the criteria
     */
    @GetMapping("courses/{courseId}/members/search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<UserNameAndLoginDTO>> searchMembersOfCourse(@PathVariable Long courseId, @RequestParam("loginOrName") String loginOrName) {
        log.debug("REST request to get members with login or name : {} in course: {}", loginOrName, courseId);

        var course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        var searchTerm = loginOrName != null ? loginOrName.toLowerCase().trim() : "";
        List<UserNameAndLoginDTO> searchResults = userRepository.searchAllWithGroupsByLoginOrNameInCourseAndReturnList(Pageable.ofSize(10), searchTerm, course.getId()).stream()
                .map(UserNameAndLoginDTO::of).toList();

        return ResponseEntity.ok().body(searchResults);
    }

    /**
     * GET /courses/:courseId/title : Returns the title of the course with the given id
     *
     * @param courseId the id of the course
     * @return the title of the course wrapped in an ResponseEntity or 404 Not Found if no course with that id exists
     */
    @GetMapping("courses/{courseId}/title")
    @EnforceAtLeastStudent
    @ResponseBody
    public ResponseEntity<String> getCourseTitle(@PathVariable Long courseId) {
        final var title = courseRepository.getCourseTitle(courseId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * Post /courses/:courseId/students/:studentLogin : Add the given user to the students of the course so that the student can access the course
     *
     * @param courseId     the id of the course
     * @param studentLogin the login of the user who should get student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> addStudentToCourse(@PathVariable Long courseId, @PathVariable String studentLogin) {
        log.debug("REST request to add {} as student to course : {}", studentLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return addUserToCourseGroup(studentLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getStudentGroupName(), Role.STUDENT);
    }

    /**
     * Post /courses/:courseId/tutors/:tutorLogin : Add the given user to the tutors of the course so that the student can access the course administration
     *
     * @param courseId   the id of the course
     * @param tutorLogin the login of the user who should get tutor access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/tutors/{tutorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> addTutorToCourse(@PathVariable Long courseId, @PathVariable String tutorLogin) {
        log.debug("REST request to add {} as tutors to course : {}", tutorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return addUserToCourseGroup(tutorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getTeachingAssistantGroupName(), Role.TEACHING_ASSISTANT);
    }

    /**
     * Post /courses/:courseId/editors/:editorLogin : Add the given user to the editors of the course so that the student can access the course administration
     *
     * @param courseId    the id of the course
     * @param editorLogin the login of the user who should get editor access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/editors/{editorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> addEditorToCourse(@PathVariable Long courseId, @PathVariable String editorLogin) {
        log.debug("REST request to add {} as editors to course : {}", editorLogin, courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        courseService.checkIfEditorGroupsNeedsToBeCreated(course);
        return addUserToCourseGroup(editorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getEditorGroupName(), Role.EDITOR);
    }

    /**
     * Post /courses/:courseId/instructors/:instructorLogin : Add the given user to the instructors of the course so that the student can access the course administration
     *
     * @param courseId        the id of the course
     * @param instructorLogin the login of the user who should get instructors access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/instructors/{instructorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> addInstructorToCourse(@PathVariable Long courseId, @PathVariable String instructorLogin) {
        log.debug("REST request to add {} as instructors to course : {}", instructorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return addUserToCourseGroup(instructorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getInstructorGroupName(), Role.INSTRUCTOR);
    }

    /**
     * adds the userLogin to the group (student, tutors or instructors) of the given course
     *
     * @param userLogin         the user login of the student, tutor or instructor who should be added to the group
     * @param instructorOrAdmin the user who initiates this request who must be an instructor of the given course or an admin
     * @param course            the course which is only passes to check if the instructorOrAdmin is an instructor of the course
     * @param group             the group to which the userLogin should be added
     * @param role              the role which should be added
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found) or with status 403 (Forbidden)
     */
    @NotNull
    public ResponseEntity<Void> addUserToCourseGroup(String userLogin, User instructorOrAdmin, Course course, String group, Role role) {
        if (authCheckService.isAtLeastInstructorInCourse(course, instructorOrAdmin)) {
            Optional<User> userToAddToGroup = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userLogin);
            if (userToAddToGroup.isEmpty()) {
                throw new EntityNotFoundException("User", userLogin);
            }
            courseService.addUserToGroup(userToAddToGroup.get(), group);
            if (role == Role.STUDENT && course.getLearningPathsEnabled()) {
                Course courseWithCompetencies = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(course.getId());
                learningPathService.generateLearningPathForUser(courseWithCompetencies, userToAddToGroup.get());
            }
            return ResponseEntity.ok().body(null);
        }
        else {
            throw new AccessForbiddenException();
        }
    }

    /**
     * DELETE /courses/:courseId/students/:studentLogin : Remove the given user from the students of the course so that the student cannot access the course anymore
     *
     * @param courseId     the id of the course
     * @param studentLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping("courses/{courseId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeStudentFromCourse(@PathVariable Long courseId, @PathVariable String studentLogin) {
        log.debug("REST request to remove {} as student from course : {}", studentLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(studentLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getStudentGroupName());
    }

    /**
     * DELETE /courses/:courseId/tutors/:tutorsLogin : Remove the given user from the tutors of the course so that the tutors cannot access the course administration anymore
     *
     * @param courseId   the id of the course
     * @param tutorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping("courses/{courseId}/tutors/{tutorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeTutorFromCourse(@PathVariable Long courseId, @PathVariable String tutorLogin) {
        log.debug("REST request to remove {} as tutor from course : {}", tutorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(tutorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getTeachingAssistantGroupName());
    }

    /**
     * DELETE /courses/:courseId/editors/:editorsLogin : Remove the given user from the editors of the course so that the editors cannot access the course administration anymore
     *
     * @param courseId    the id of the course
     * @param editorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping("courses/{courseId}/editors/{editorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeEditorFromCourse(@PathVariable Long courseId, @PathVariable String editorLogin) {
        log.debug("REST request to remove {} as editor from course : {}", editorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(editorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getEditorGroupName());
    }

    /**
     * DELETE /courses/:courseId/instructors/:instructorLogin : Remove the given user from the instructors of the course so that the instructor cannot access the course
     * administration anymore
     *
     * @param courseId        the id of the course
     * @param instructorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping("courses/{courseId}/instructors/{instructorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeInstructorFromCourse(@PathVariable Long courseId, @PathVariable String instructorLogin) {
        log.debug("REST request to remove {} as instructor from course : {}", instructorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(instructorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getInstructorGroupName());
    }

    /**
     * removes the userLogin from the group (student, tutors or instructors) of the given course
     *
     * @param userLogin         the user login of the student, tutor or instructor who should be removed from the group
     * @param instructorOrAdmin the user who initiates this request who must be an instructor of the given course or an admin
     * @param course            the course which is only passes to check if the instructorOrAdmin is an instructor of the course
     * @param group             the group from which the userLogin should be removed
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found) or with status 403 (Forbidden)
     */
    @NotNull
    public ResponseEntity<Void> removeUserFromCourseGroup(String userLogin, User instructorOrAdmin, Course course, String group) {
        if (!authCheckService.isAtLeastInstructorInCourse(course, instructorOrAdmin)) {
            throw new AccessForbiddenException();
        }
        Optional<User> userToRemoveFromGroup = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userLogin);
        if (userToRemoveFromGroup.isEmpty()) {
            throw new EntityNotFoundException("User", userLogin);
        }
        courseService.removeUserFromGroup(userToRemoveFromGroup.get(), group);
        return ResponseEntity.ok().body(null);
    }

    /**
     * GET /courses/{courseId}/management-detail : Gets the data needed for the course management detail view
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the body, or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/management-detail")
    @EnforceAtLeastTutor
    public ResponseEntity<CourseManagementDetailViewDTO> getCourseDTOForDetailView(@PathVariable Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        GradingScale gradingScale = gradingScaleService.findGradingScaleByCourseId(courseId).orElse(null);
        CourseManagementDetailViewDTO managementDetailViewDTO = courseService.getStatsForDetailView(course, gradingScale);
        return ResponseEntity.ok(managementDetailViewDTO);
    }

    /**
     * GET /courses/:courseId/statistics : Get the active students for this particular course
     *
     * @param courseId    the id of the course
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one period in the past, -2 is two periods in the past
     * @param periodSize  optional size of the period, default is 17
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/statistics")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Integer>> getActiveStudentsForCourseDetailView(@PathVariable Long courseId, @RequestParam Long periodIndex,
            @RequestParam Optional<Integer> periodSize) {
        var course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        var exerciseIds = exerciseRepository.findAllIdsByCourseId(courseId);
        var chartEndDate = courseService.determineEndDateForActiveStudents(course);
        var spanEndDate = chartEndDate.plusWeeks(periodSize.orElse(17) * periodIndex);
        var returnedSpanSize = courseService.determineTimeSpanSizeForActiveStudents(course, spanEndDate, periodSize.orElse(17));
        var activeStudents = courseService.getActiveStudents(exerciseIds, periodIndex, Math.min(returnedSpanSize, periodSize.orElse(17)), chartEndDate);
        return ResponseEntity.ok(activeStudents);
    }

    /**
     * GET /courses/:courseId/statistics-lifetime-overview : Get the active students for this particular course over its whole lifetime
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/statistics-lifetime-overview")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Integer>> getActiveStudentsForCourseLiveTime(@PathVariable Long courseId) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, courseRepository.findByIdElseThrow(courseId), null);
        var exerciseIds = exerciseRepository.findAllIdsByCourseId(courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        if (course.getStartDate() == null) {
            throw new IllegalArgumentException("Course does not contain start date");
        }
        var endDate = courseService.determineEndDateForActiveStudents(course);
        var returnedSpanSize = courseService.calculateWeeksBetweenDates(course.getStartDate(), endDate);
        var activeStudents = courseService.getActiveStudents(exerciseIds, 0, Math.toIntExact(returnedSpanSize), endDate);
        return ResponseEntity.ok(activeStudents);
    }

    /**
     * POST /courses/:courseId/:courseGroup : Add multiple users to the user group of the course so that they can access the course
     * The passed list of UserDTOs must include at least one unique user identifier (i.e. registration number OR email OR login)
     * <p>
     * This method first tries to find the student in the internal Artemis user database (because the user is probably already using Artemis).
     * In case the user cannot be found, it additionally searches the connected LDAP in case it is configured.
     *
     * @param courseId    the id of the course
     * @param studentDtos the list of students (with at one unique user identifier) who should get access to the course
     * @param courseGroup the group, the user has to be added to, either 'students', 'tutors', 'instructors' or 'editors'
     * @return the list of students who could not be registered for the course, because they could NOT be found in the Artemis database and could NOT be found in the connected LDAP
     */
    @PostMapping("courses/{courseId}/{courseGroup}")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<StudentDTO>> addUsersToCourseGroup(@PathVariable Long courseId, @PathVariable String courseGroup, @RequestBody List<StudentDTO> studentDtos) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, courseRepository.findByIdElseThrow(courseId), null);
        log.debug("REST request to add {} as {} to course {}", studentDtos, courseGroup, courseId);
        List<StudentDTO> notFoundStudentsDtos = courseService.registerUsersForCourseGroup(courseId, studentDtos, courseGroup);
        return ResponseEntity.ok().body(notFoundStudentsDtos);
    }

    /**
     * GET courses/{courseId}/allowed-complaints: Get the number of complaints that a student or team is still allowed to submit in the given course.
     * It is determined by the max. complaint limit and the current number of open or rejected complaints of the student or team in the course.
     * Students use their personal complaints for individual exercises and team complaints for team-based exercises, i.e. each student has
     * maxComplaints for personal complaints and additionally maxTeamComplaints for complaints by their team in the course.
     *
     * @param courseId the id of the course for which we want to get the number of allowed complaints
     * @param teamMode whether to return the number of allowed complaints per team (instead of per student)
     * @return the ResponseEntity with status 200 (OK) and the number of still allowed complaints
     */
    @GetMapping("courses/{courseId}/allowed-complaints")
    @EnforceAtLeastStudent
    public ResponseEntity<Long> getNumberOfAllowedComplaintsInCourse(@PathVariable Long courseId, @RequestParam(defaultValue = "false") Boolean teamMode) {
        log.debug("REST request to get the number of unaccepted Complaints associated to the current user in course : {}", courseId);
        User user = userRepository.getUser();
        Participant participant = user;
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!course.getComplaintsEnabled()) {
            throw new BadRequestAlertException("Complaints are disabled for this course", COMPLAINT_ENTITY_NAME, "complaintsDisabled");
        }
        if (teamMode) {
            Optional<Team> team = teamRepository.findAllByCourseIdAndUserIdOrderByIdDesc(course.getId(), user.getId()).stream().findFirst();
            participant = team.orElseThrow(() -> new BadRequestAlertException("You do not belong to a team in this course.", COMPLAINT_ENTITY_NAME, "noAssignedTeamInCourse"));
        }
        long unacceptedComplaints = complaintService.countUnacceptedComplaintsByParticipantAndCourseId(participant, courseId);
        return ResponseEntity.ok(Math.max(complaintService.getMaxComplaintsPerParticipant(course, participant) - unacceptedComplaints, 0));
    }
}
