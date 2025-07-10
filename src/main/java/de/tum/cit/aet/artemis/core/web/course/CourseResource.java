package de.tum.cit.aet.artemis.core.web.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentDashboardService;
import de.tum.cit.aet.artemis.assessment.service.ComplaintService;
import de.tum.cit.aet.artemis.assessment.service.CourseScoreCalculationService;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.api.LearningPathApi;
import de.tum.cit.aet.artemis.communication.service.ConductAgreementService;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseExistingExerciseDetailsDTO;
import de.tum.cit.aet.artemis.core.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.core.dto.CourseForImportDTO;
import de.tum.cit.aet.artemis.core.dto.CoursesForDashboardDTO;
import de.tum.cit.aet.artemis.core.dto.OnlineCourseDTO;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.course.CourseForUserGroupService;
import de.tum.cit.aet.artemis.core.service.course.CourseLoadService;
import de.tum.cit.aet.artemis.core.service.course.CourseOverviewService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.programming.service.ci.CIUserManagementService;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupChannelManagementApi;

/**
 * REST controller for managing Course.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/")
public class CourseResource {

    private static final String ENTITY_NAME = "course";

    private static final String COMPLAINT_ENTITY_NAME = "complaint";

    private static final Logger log = LoggerFactory.getLogger(CourseResource.class);

    private static final int MAX_TITLE_LENGTH = 255;

    private final CourseLoadService courseLoadService;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<LtiApi> ltiApi;

    private final CourseRepository courseRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final SubmissionService submissionService;

    private final AssessmentDashboardService assessmentDashboardService;

    private final Optional<CIUserManagementService> optionalCiUserManagementService;

    private final ExerciseRepository exerciseRepository;

    private final FileService fileService;

    private final Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi;

    private final CourseScoreCalculationService courseScoreCalculationService;

    private final GradingScaleRepository gradingScaleRepository;

    private final ConductAgreementService conductAgreementService;

    private final Optional<AthenaApi> athenaApi;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<LearningPathApi> learningPathApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final ComplaintService complaintService;

    private final TeamRepository teamRepository;

    private final CourseForUserGroupService courseForUserGroupService;

    private final CourseOverviewService courseOverviewService;

    public CourseResource(UserRepository userRepository, CourseService courseService, CourseRepository courseRepository, Optional<LtiApi> ltiApi,
            AuthorizationCheckService authCheckService, TutorParticipationRepository tutorParticipationRepository, SubmissionService submissionService,
            AssessmentDashboardService assessmentDashboardService, ExerciseRepository exerciseRepository, Optional<CIUserManagementService> optionalCiUserManagementService,
            FileService fileService, Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi, CourseScoreCalculationService courseScoreCalculationService,
            GradingScaleRepository gradingScaleRepository, Optional<LearningPathApi> learningPathApi, ConductAgreementService conductAgreementService,
            Optional<AthenaApi> athenaApi, Optional<ExamRepositoryApi> examRepositoryApi, ComplaintService complaintService, TeamRepository teamRepository,
            Optional<LearnerProfileApi> learnerProfileApi, CourseForUserGroupService courseForUserGroupService, CourseOverviewService courseOverviewService,
            CourseLoadService courseLoadService) {
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.ltiApi = ltiApi;
        this.authCheckService = authCheckService;
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.submissionService = submissionService;
        this.optionalCiUserManagementService = optionalCiUserManagementService;
        this.assessmentDashboardService = assessmentDashboardService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.fileService = fileService;
        this.tutorialGroupChannelManagementApi = tutorialGroupChannelManagementApi;
        this.courseScoreCalculationService = courseScoreCalculationService;
        this.gradingScaleRepository = gradingScaleRepository;
        this.learningPathApi = learningPathApi;
        this.conductAgreementService = conductAgreementService;
        this.athenaApi = athenaApi;
        this.examRepositoryApi = examRepositoryApi;
        this.complaintService = complaintService;
        this.teamRepository = teamRepository;
        this.learnerProfileApi = learnerProfileApi;
        this.courseForUserGroupService = courseForUserGroupService;
        this.courseOverviewService = courseOverviewService;
        this.courseLoadService = courseLoadService;
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

        if (!authCheckService.isAdmin(user)) {
            // this means the user must be an instructor, who has NO Admin rights.
            // instructors are not allowed to change group names, because this would lead to security problems
            final var changedGroupNames = getChangedGroupNames(courseUpdate, existingCourse);
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
            Path basePath = FilePathConverter.getCourseIconFilePath();
            Path savePath = FileUtil.saveFile(file, basePath, FilePathType.COURSE_ICON, false);
            courseUpdate.setCourseIcon(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.COURSE_ICON, courseId).toString());
            if (existingCourse.getCourseIcon() != null) {
                // delete old course icon
                fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(new URI(existingCourse.getCourseIcon()), FilePathType.COURSE_ICON), 0);
            }
        }
        else if (courseUpdate.getCourseIcon() == null && existingCourse.getCourseIcon() != null) {
            // delete old course icon
            fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(new URI(existingCourse.getCourseIcon()), FilePathType.COURSE_ICON), 0);
        }

        if (courseUpdate.isOnlineCourse() != existingCourse.isOnlineCourse()) {
            if (courseUpdate.isOnlineCourse() && ltiApi.isPresent()) {
                ltiApi.get().createOnlineCourseConfiguration(courseUpdate);
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
        if (existingCourse.getLearningPathsEnabled() != courseUpdate.getLearningPathsEnabled() && courseUpdate.getLearningPathsEnabled() && learningPathApi.isPresent()) {
            Course courseWithCompetencies = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(result.getId());
            Set<User> students = userRepository.getStudentsWithLearnerProfile(courseWithCompetencies);
            learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfiles(courseWithCompetencies, students));
            learningPathApi.ifPresent(api -> api.generateLearningPaths(courseWithCompetencies));
        }

        // if access to restricted athena modules got disabled for the course, we need to set all exercises that use restricted modules to null
        if (athenaModuleAccessChanged && !courseUpdate.getRestrictedAthenaModulesAccess()) {
            athenaApi.ifPresent(api -> api.revokeAccessToRestrictedFeedbackSuggestionModules(result));
        }

        // Based on the old instructors, editors and TAs, we can update all exercises in the course in the VCS (if necessary)
        // We need the old instructors, editors and TAs, so that the VCS user management service can determine which
        // users no longer have TA, editor or instructor rights in the related exercise repositories.
        final var oldInstructorGroup = existingCourse.getInstructorGroupName();
        final var oldEditorGroup = existingCourse.getEditorGroupName();
        final var oldTeachingAssistantGroup = existingCourse.getTeachingAssistantGroupName();

        optionalCiUserManagementService
                .ifPresent(ciUserManagementService -> ciUserManagementService.updateCoursePermissions(result, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup));
        if (timeZoneChanged && tutorialGroupChannelManagementApi.isPresent()) {
            tutorialGroupChannelManagementApi.get().onTimeZoneUpdate(result);
        }
        return ResponseEntity.ok(result);
    }

    private static Set<String> getChangedGroupNames(Course courseUpdate, Course existingCourse) {
        Set<String> existingGroupNames = new HashSet<>(List.of(existingCourse.getStudentGroupName(), existingCourse.getTeachingAssistantGroupName(),
                existingCourse.getEditorGroupName(), existingCourse.getInstructorGroupName()));
        Set<String> newGroupNames = new HashSet<>(List.of(courseUpdate.getStudentGroupName(), courseUpdate.getTeachingAssistantGroupName(), courseUpdate.getEditorGroupName(),
                courseUpdate.getInstructorGroupName()));
        Set<String> changedGroupNames = new HashSet<>(newGroupNames);
        changedGroupNames.removeAll(existingGroupNames);
        return changedGroupNames;
    }

    /**
     * GET courses/for-lti-dashboard : Retrieves a list of online courses for a specific LTI dashboard based on the client ID.
     *
     * @param clientId the client ID of the LTI platform used to filter the courses.
     * @return a {@link ResponseEntity} containing a list of {@link OnlineCourseDTO} for the courses the user has access to.
     */
    @GetMapping("courses/for-lti-dashboard")
    @EnforceAtLeastInstructor
    @Profile(PROFILE_LTI)
    public ResponseEntity<List<OnlineCourseDTO>> findAllOnlineCoursesForLtiDashboard(@RequestParam("clientId") String clientId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get all online courses the user {} has access to", user.getLogin());

        Set<Course> courses = courseService.findAllOnlineCoursesForPlatformForUser(clientId, user);

        List<OnlineCourseDTO> onlineCourseDTOS = courses.stream().map(c -> new OnlineCourseDTO(c.getId(), c.getTitle(), c.getShortName(),
                c.getOnlineCourseConfiguration().getLtiPlatformConfiguration().getRegistrationId(), c.getStartDate(), c.getEndDate(), c.getDescription(), c.getNumberOfStudents()))
                .toList();

        return ResponseEntity.ok(onlineCourseDTOS);
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
        log.debug("REST request to get all courses the user has access to");
        User user = userRepository.getUserWithGroupsAndAuthorities();
        List<Course> courses = courseForUserGroupService.getCoursesForTutors(user, onlyActive);
        return ResponseEntity.ok(courses);
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
     * GET /courses/course-overview : get all courses for the management overview
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return the ResponseEntity with status 200 (OK) and with body a list of courses (the user has access to)
     */
    @GetMapping("courses/course-management-overview")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Course>> getCoursesForManagementOverview(@RequestParam(defaultValue = "false") boolean onlyActive) {
        return ResponseEntity.ok(courseOverviewService.getAllCoursesForManagementOverview(onlyActive));
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
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<CourseForDashboardDTO> getCourseForDashboard(@PathVariable long courseId) {
        long timeNanoStart = System.nanoTime();
        log.debug("REST request to get one course {} with exams, lectures, exercises, participations, submissions and results, etc.", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        Course course = courseService.findOneWithExercisesAndLecturesAndExamsAndCompetenciesAndTutorialGroupsAndFaqForUser(courseId, user);
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
        log.debug("courseService.fetchParticipationsWithSubmissionsAndResultsForCourses done in getCourseForDashboard");
        courseService.fetchPlagiarismCasesForCourseExercises(course.getExercises(), user.getId());
        log.debug("courseService.fetchPlagiarismCasesForCourseExercises done in getCourseForDashboard");
        GradingScale gradingScale = gradingScaleRepository.findByCourseId(course.getId()).orElse(null);
        log.debug("gradingScaleRepository.findByCourseId done in getCourseForDashboard");
        CourseForDashboardDTO courseForDashboardDTO = courseScoreCalculationService.getScoresAndParticipationResults(course, gradingScale, user.getId(), true);
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
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<CoursesForDashboardDTO> getCoursesForDashboard() {
        long timeNanoStart = System.nanoTime();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("Request to get all courses user {} has access to with exams, lectures, exercises, participations, submissions and results + calculated scores", user.getLogin());
        var activeCoursesForUser = courseService.findAllActiveForUser(user);
        log.debug("courseService.findAllActiveWithExercisesForUser done");
        var allUserCourseGrades = courseService.fetchCoursesGradesForUser(activeCoursesForUser, user);
        log.debug("courseService.fetchCourseGradesForUser done");

        var courseIds = activeCoursesForUser.stream().map(Course::getId).collect(Collectors.toSet());
        // we explicitly add 1 hour to the end date to compensate for potential write extensions. Calculating it exactly is not feasible here
        Set<Exam> activeExams = examRepositoryApi.isPresent()
                ? examRepositoryApi.get().findActiveExams(courseIds, user.getId(), ZonedDateTime.now(), ZonedDateTime.now().plusHours(1))
                : Set.of();

        var allExercises = exerciseRepository.findCourseExerciseScoreInformationByCourseIds(courseIds);

        Set<CourseForDashboardDTO> coursesForDashboard = new HashSet<>();
        for (Course course : activeCoursesForUser) {
            var courseExercises = allExercises.stream().filter(exercise -> exercise.courseId().equals(course.getId())).collect(Collectors.toSet());
            var courseExerciseIds = courseExercises.stream().map(ExerciseCourseScoreDTO::id).collect(Collectors.toSet());
            var userCourseGrades = allUserCourseGrades.stream().filter(grade -> courseExerciseIds.contains(grade.exerciseId())).collect(Collectors.toSet());
            CourseForDashboardDTO courseForDashboardDTO = courseScoreCalculationService.calculateCourseScore(course, courseExercises, userCourseGrades, user.getId());
            coursesForDashboard.add(courseForDashboardDTO);
        }
        log.info("courses/for-dashboard (multiple courses) finished in {} for {} courses for user {}", TimeLogUtil.formatDurationFrom(timeNanoStart), activeCoursesForUser.size(),
                user.getLogin());
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
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<Course> getCourseForAssessmentDashboard(@PathVariable long courseId) {
        log.debug("REST request /courses/{courseId}/for-assessment-dashboard");
        Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);

        Set<Exercise> interestingExercises = courseRepository.filterInterestingExercisesForAssessmentDashboards(course.getExercises());
        course.setExercises(interestingExercises);

        User user = userRepository.getUser();
        List<TutorParticipation> tutorParticipations = tutorParticipationRepository.findAllByAssessedExercise_Course_IdAndTutor_Id(course.getId(), user.getId());
        assessmentDashboardService.generateStatisticsForExercisesForAssessmentDashboard(course.getExercises(), tutorParticipations, false);
        return ResponseEntity.ok(course);
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
        log.debug("REST request to get course {} for students", courseId);
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
        log.debug("REST request to get course {} for tutors", courseId);
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        return ResponseEntity.ok(course);
    }

    /**
     * GET /courses/:courseId : get the "id" course.
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/with-exercises-lectures-competencies")
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<Course> getCourseWithExercisesAndLecturesAndCompetencies(@PathVariable Long courseId) {
        log.debug("REST request to get course {} for tutors", courseId);
        return ResponseEntity.ok(courseLoadService.loadCourseWithExercisesLecturesLectureUnitsCompetenciesAndPrerequisites(courseId));
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
     * GET /courses/:courseId/locked-submissions Get locked submissions for course for user
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/locked-submissions")
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
     * GET /courses/{courseId}/all-exercises-with-due-dates : Returns all exercises in a course with their titles,
     * due dates and categories
     *
     * @param courseId the id of the course
     * @return Set of exercises with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/all-exercises-with-due-dates")
    @EnforceAtLeastTutor
    public ResponseEntity<Set<Exercise>> getAllExercisesWithDueDatesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all exercises with due dates and categories in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);

        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        Set<Exercise> exercises = exerciseRepository.findByCourseIdWithFutureDueDatesAndCategories(courseId);
        return ResponseEntity.ok(exercises);
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
        for (final var course : courseOverviewService.getAllCoursesForManagementOverview(onlyActive)) {
            course.setExercises(exerciseRepository.getExercisesForCourseManagementOverview(course.getId()));
            courses.add(course);
        }
        return ResponseEntity.ok(courses);
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

    /**
     * GET courses/{courseId}/existing-exercise-details: Get the exercise names (and shortNames for {@link ExerciseType#PROGRAMMING} exercises)
     * of all exercises with the given type in the given course.
     *
     * @param courseId     of the course for which all exercise names should be fetched
     * @param exerciseType for which the details should be fetched, as the name of an exercise only needs to be unique for each exercise type
     * @return {@link CourseExistingExerciseDetailsDTO} with the exerciseNames (and already used shortNames if a {@link ExerciseType#PROGRAMMING} exercise is requested)
     */
    @GetMapping("courses/{courseId}/existing-exercise-details")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<CourseExistingExerciseDetailsDTO> getExistingExerciseDetails(@PathVariable Long courseId, @RequestParam String exerciseType) {
        log.debug("REST request to get details of existing exercises in course : {}", courseId);
        Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);

        Set<String> alreadyTakenExerciseNames = new HashSet<>();
        Set<String> alreadyTakenShortNames = new HashSet<>();

        boolean includeShortNames = exerciseType.equals(ExerciseType.PROGRAMMING.toString());

        course.getExercises().forEach((exercise -> {
            if (exercise.getType().equals(exerciseType)) {
                alreadyTakenExerciseNames.add(exercise.getTitle());
                if (includeShortNames && exercise.getShortName() != null) {
                    alreadyTakenShortNames.add(exercise.getShortName());
                }
            }
        }));

        return ResponseEntity.ok(new CourseExistingExerciseDetailsDTO(alreadyTakenExerciseNames, alreadyTakenShortNames));
    }
}
