package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.SHORT_NAME_PATTERN;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;
import static java.time.ZonedDateTime.now;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.dto.StatsForInstructorDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.config.JHipsterConstants;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class CourseResource {

    private final Logger log = LoggerFactory.getLogger(CourseResource.class);

    private static final String ENTITY_NAME = "course";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Environment env;

    private final UserService userService;

    private final CourseService courseService;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final ExerciseService exerciseService;

    private final Optional<ArtemisAuthenticationProvider> artemisAuthenticationProvider;

    private final TutorParticipationService tutorParticipationService;

    private final LectureService lectureService;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final NotificationService notificationService;

    private final TextSubmissionService textSubmissionService;

    private final ModelingSubmissionService modelingSubmissionService;

    private final FileUploadSubmissionService fileUploadSubmissionService;

    private final ResultService resultService;

    private final ComplaintService complaintService;

    private final TutorLeaderboardService tutorLeaderboardService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final AuditEventRepository auditEventRepository;

    public CourseResource(Environment env, UserService userService, CourseService courseService, ParticipationService participationService, CourseRepository courseRepository,
            ExerciseService exerciseService, AuthorizationCheckService authCheckService, TutorParticipationService tutorParticipationService,
            Optional<ArtemisAuthenticationProvider> artemisAuthenticationProvider, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository,
            LectureService lectureService, NotificationService notificationService, TextSubmissionService textSubmissionService,
            FileUploadSubmissionService fileUploadSubmissionService, ModelingSubmissionService modelingSubmissionService, ResultService resultService,
            ComplaintService complaintService, TutorLeaderboardService tutorLeaderboardService, ProgrammingExerciseService programmingExerciseService,
            ExampleSubmissionRepository exampleSubmissionRepository, AuditEventRepository auditEventRepository) {
        this.env = env;
        this.userService = userService;
        this.courseService = courseService;
        this.participationService = participationService;
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.lectureService = lectureService;
        this.notificationService = notificationService;
        this.textSubmissionService = textSubmissionService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.resultService = resultService;
        this.complaintService = complaintService;
        this.tutorLeaderboardService = tutorLeaderboardService;
        this.fileUploadSubmissionService = fileUploadSubmissionService;
        this.programmingExerciseService = programmingExerciseService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * POST /courses : Create a new course.
     *
     * @param course the course to create
     * @return the ResponseEntity with status 201 (Created) and with body the new course, or with status 400 (Bad Request) if the course has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Course> createCourse(@RequestBody Course course) throws URISyntaxException {
        log.debug("REST request to save Course : {}", course);
        if (course.getId() != null) {
            throw new BadRequestAlertException("A new course cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            // Check if course shortname matches regex
            Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(course.getShortName());
            if (!shortNameMatcher.matches()) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The shortname is invalid", "shortnameInvalid")).body(null);
            }
            checkIfGroupsExists(course);
            Course result = courseService.save(course);
            return ResponseEntity.created(new URI("/api/courses/" + result.getId()))
                    .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
        }
        catch (ArtemisAuthenticationException ex) {
            // a specified group does not exist, notify the client
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "groupNotFound", ex.getMessage())).body(null);
        }
    }

    /**
     * PUT /courses : Updates an existing updatedCourse.
     *
     * @param updatedCourse the updatedCourse to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated updatedCourse, or with status 400 (Bad Request) if the updatedCourse is not valid, or with status
     *         500 (Internal Server Error) if the updatedCourse couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Course> updateCourse(@RequestBody Course updatedCourse) throws URISyntaxException {
        log.debug("REST request to update Course : {}", updatedCourse);
        if (updatedCourse.getId() == null) {
            return createCourse(updatedCourse);
        }
        Optional<Course> existingCourse = courseRepository.findById(updatedCourse.getId());
        if (existingCourse.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        // only allow admins or instructors of the existing updatedCourse to change it
        // this is important, otherwise someone could put himself into the instructor group of the updated Course
        if (user.getGroups().contains(existingCourse.get().getInstructorGroupName()) || authCheckService.isAdmin()) {
            try {
                // Check if course shortname matches regex
                Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(updatedCourse.getShortName());
                if (!shortNameMatcher.matches()) {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The shortname is invalid", "shortnameInvalid")).body(null);
                }
                checkIfGroupsExists(updatedCourse);
                Course result = courseService.save(updatedCourse);
                return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedCourse.getTitle())).body(result);
            }
            catch (ArtemisAuthenticationException ex) {
                // a specified group does not exist, notify the client
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, ex.getMessage(), "groupNotFound")).body(null);
            }
        }
        else {
            return forbidden();
        }
    }

    private void checkIfGroupsExists(Course course) {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (!activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            return;
        }
        // only execute this method in the production environment because normal developers might not have the right to call this method on the authentication server
        if (course.getInstructorGroupName() != null) {
            if (!artemisAuthenticationProvider.get().checkIfGroupExists(course.getInstructorGroupName())) {
                throw new ArtemisAuthenticationException(
                        "Cannot save! The group " + course.getInstructorGroupName() + " for instructors does not exist. Please double check the instructor group name!");
            }
        }
        if (course.getTeachingAssistantGroupName() != null) {
            if (!artemisAuthenticationProvider.get().checkIfGroupExists(course.getTeachingAssistantGroupName())) {
                throw new ArtemisAuthenticationException("Cannot save! The group " + course.getTeachingAssistantGroupName()
                        + " for teaching assistants does not exist. Please double check the teaching assistants group name!");
            }
        }
        if (course.getStudentGroupName() != null) {
            if (!artemisAuthenticationProvider.get().checkIfGroupExists(course.getStudentGroupName())) {
                throw new ArtemisAuthenticationException(
                        "Cannot save! The group " + course.getStudentGroupName() + " for students does not exist. Please double check the students group name!");
            }
        }
    }

    /**
     * POST /courses/{courseId}/register : Register for an existing course. This method registers the current user for the given course id in case the course has already started
     * and not finished yet. The user is added to the course student group in the Authentication System and the course student group is added to the user's groups in the Artemis
     * database.
     * @param courseId to find the course
     * @return response entity for user who has been registered to the course
     */
    @PostMapping("/courses/{courseId}/register")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<User> registerForCourse(@PathVariable Long courseId) {
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to register {} for Course {}", user.getFirstName(), course.getTitle());
        if (course.getStartDate() != null && course.getStartDate().isAfter(now())) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "courseNotStarted", "The course has not yet started. Cannot register user"))
                    .body(null);
        }
        if (course.getEndDate() != null && course.getEndDate().isBefore(now())) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "courseAlreadyFinished", "The course has already finished. Cannot register user"))
                    .body(null);
        }
        if (course.isRegistrationEnabled() != Boolean.TRUE) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "registrationDisabled", "The course does not allow registration. Cannot register user"))
                    .body(null);
        }
        artemisAuthenticationProvider.get().registerUserForCourse(user, course);
        return ResponseEntity.ok(user);
    }

    /**
     * GET /courses : get all courses for administration purposes.
     *
     * @return the list of courses (the user has access to)
     */
    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public List<Course> getAllCourses() {
        log.debug("REST request to get all Courses the user has access to");
        User user = userService.getUserWithGroupsAndAuthorities();
        List<Course> courses = courseService.findAll();
        Stream<Course> userCourses = courses.stream().filter(course -> user.getGroups().contains(course.getTeachingAssistantGroupName())
                || user.getGroups().contains(course.getInstructorGroupName()) || authCheckService.isAdmin());
        return userCourses.collect(Collectors.toList());
    }

    /**
     * GET /courses : get all courses that the current user can register to. Decided by the start and end date and if the registrationEnabled flag is set correctly
     *
     * @return the list of courses which are active)
     */
    @GetMapping("/courses/to-register")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public List<Course> getAllCoursesToRegister() {
        log.debug("REST request to get all currently active Courses that are not online courses");
        return courseService.findAllCurrentlyActiveAndNotOnlineAndEnabled();
    }

    /**
     * GET /courses/for-dashboard
     *
     * @return the list of courses (the user has access to) including all exercises with participation and result for the user
     */
    @GetMapping("/courses/for-dashboard")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public List<Course> getAllCoursesForDashboard() {
        long start = System.currentTimeMillis();
        log.debug("REST request to get all Courses the user has access to with exercises, participations and results");
        log.debug("/courses/for-dashboard.start");
        User user = userService.getUserWithGroupsAndAuthorities();

        // get all courses with exercises for this user
        List<Course> courses = courseService.findAllActiveWithExercisesAndLecturesForUser(user);
        Set<Exercise> activeExercises = courses.stream().flatMap(course -> course.getExercises().stream()).collect(Collectors.toSet());
        log.debug("          /courses/for-dashboard.findAllActiveWithExercisesForUser in " + (System.currentTimeMillis() - start) + "ms");

        if (activeExercises.isEmpty()) {
            return courses;
        }

        List<StudentParticipation> participations = participationService.findWithSubmissionsWithResultByStudentIdAndExercise(user.getId(), activeExercises);
        log.debug("          /courses/for-dashboard.findWithSubmissionsWithResultByStudentIdAndExercise in " + (System.currentTimeMillis() - start) + "ms");

        for (Course course : courses) {
            boolean isStudent = !authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
            for (Exercise exercise : course.getExercises()) {
                // add participation with submission and result to each exercise
                exercise.filterForCourseDashboard(participations, user.getLogin(), isStudent);
                // remove sensitive information from the exercise for students
                if (isStudent) {
                    exercise.filterSensitiveInformation();
                }
            }
        }
        log.info("/courses/for-dashboard.done in " + (System.currentTimeMillis() - start) + "ms for " + courses.size() + " courses with " + activeExercises.size()
                + " exercises for user " + user.getLogin());

        return courses;
    }

    /**
     * GET /courses/:courseId/for-tutor-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseForTutorDashboard(@PathVariable Long courseId) {
        log.debug("REST request /courses/{courseId}/for-tutor-dashboard");
        Course course = courseService.findOneWithExercises(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermission(course, user)) {
            return forbidden();
        }

        Set<Exercise> interestingExercises = course.getInterestingExercisesForAssessmentDashboards();
        course.setExercises(interestingExercises);

        List<TutorParticipation> tutorParticipations = tutorParticipationService.findAllByCourseAndTutor(course, user);

        for (Exercise exercise : interestingExercises) {

            // TODO: This could be 1 repository method as the exercise id is provided anyway.
            long numberOfSubmissions = 0L;
            if (exercise instanceof TextExercise) {
                numberOfSubmissions = textSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
            }
            else if (exercise instanceof ModelingExercise) {
                numberOfSubmissions += modelingSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
            }
            else if (exercise instanceof FileUploadExercise) {
                numberOfSubmissions += fileUploadSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
            }
            else if (exercise instanceof ProgrammingExercise) {
                numberOfSubmissions += programmingExerciseService.countSubmissions(exercise.getId());
            }

            long numberOfAssessments = resultService.countNumberOfFinishedAssessmentsForExercise(exercise.getId());

            exercise.setNumberOfParticipations(numberOfSubmissions);
            exercise.setNumberOfAssessments(numberOfAssessments);

            List<ExampleSubmission> exampleSubmissions = this.exampleSubmissionRepository.findAllByExerciseId(exercise.getId());
            // Do not provide example submissions without any assessment
            exampleSubmissions.removeIf(exampleSubmission -> exampleSubmission.getSubmission() == null || exampleSubmission.getSubmission().getResult() == null);
            exercise.setExampleSubmissions(new HashSet<>(exampleSubmissions));

            TutorParticipation tutorParticipation = tutorParticipations.stream().filter(participation -> participation.getAssessedExercise().getId().equals(exercise.getId()))
                    .findFirst().orElseGet(() -> {
                        TutorParticipation emptyTutorParticipation = new TutorParticipation();
                        emptyTutorParticipation.setStatus(TutorParticipationStatus.NOT_PARTICIPATED);
                        return emptyTutorParticipation;
                    });
            exercise.setTutorParticipations(Collections.singleton(tutorParticipation));
        }

        return ResponseUtil.wrapOrNotFound(Optional.of(course));
    }

    /**
     * GET /courses/:courseId/stats-for-tutor-dashboard A collection of useful statistics for the tutor course dashboard, including: - number of submissions to the course - number of
     * assessments - number of assessments assessed by the tutor - number of complaints
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/stats-for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StatsForInstructorDashboardDTO> getStatsForTutorDashboard(@PathVariable Long courseId) {
        log.debug("REST request /courses/{courseId}/stats-for-tutor-dashboard");

        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermission(course, user)) {
            return forbidden();
        }
        StatsForInstructorDashboardDTO stats = new StatsForInstructorDashboardDTO();

        Long numberOfSubmissions = textSubmissionService.countSubmissionsToAssessByCourseId(courseId) + modelingSubmissionService.countSubmissionsToAssessByCourseId(courseId)
                + fileUploadSubmissionService.countSubmissionsToAssessByCourseId(courseId) + programmingExerciseService.countSubmissionsToAssessByCourseId(courseId);
        stats.setNumberOfSubmissions(numberOfSubmissions);

        Long numberOfAssessments = resultService.countNumberOfAssessments(courseId);
        stats.setNumberOfAssessments(numberOfAssessments);

        Long numberOfMoreFeedbackRequests = complaintService.countMoreFeedbackRequestsByCourseId(courseId);
        stats.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);

        Long numberOfComplaints = complaintService.countComplaintsByCourseId(courseId);
        stats.setNumberOfComplaints(numberOfComplaints);

        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getCourseLeaderboard(course);
        stats.setTutorLeaderboardEntries(leaderboardEntries);

        return ResponseEntity.ok(stats);
    }

    /**
     * GET /courses/:courseId : get the "id" course.
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourse(@PathVariable Long courseId) {
        log.debug("REST request to get Course : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermission(course, user)) {
            return forbidden();
        }

        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    /**
     * GET /courses/:courseId : get the "id" course.
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/with-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseWithExercises(@PathVariable Long courseId) {
        log.debug("REST request to get Course : {}", courseId);
        Course course = courseService.findOneWithExercises(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermission(course, user)) {
            return forbidden();
        }
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    /**
     * GET /courses/:courseId/with-exercises-and-relevant-participations Get the "id" course, with text and modelling exercises and their participations It can be used only by
     * instructors for the instructor dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     * @throws AccessForbiddenException if the current user doesn't have the permission to access the course
     */
    @GetMapping("/courses/{courseId}/with-exercises-and-relevant-participations")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseWithExercisesAndRelevantParticipations(@PathVariable Long courseId) throws AccessForbiddenException {
        log.debug("REST request to get Course with exercises and relevant participations : {}", courseId);
        long start = System.currentTimeMillis();
        Course course = courseService.findOneWithExercises(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        Set<Exercise> interestingExercises = course.getInterestingExercisesForAssessmentDashboards();
        course.setExercises(interestingExercises);

        for (Exercise exercise : interestingExercises) {
            long numberOfSubmissions = 0L;
            if (exercise instanceof TextExercise) {
                numberOfSubmissions = textSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
            }
            else if (exercise instanceof ModelingExercise) {
                numberOfSubmissions += modelingSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
            }
            else if (exercise instanceof FileUploadExercise) {
                numberOfSubmissions += fileUploadSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
            }
            else if (exercise instanceof ProgrammingExercise) {
                numberOfSubmissions += programmingExerciseService.countSubmissions(exercise.getId());
            }

            long numberOfAssessments = resultService.countNumberOfFinishedAssessmentsForExercise(exercise.getId());
            long numberOfMoreFeedbackRequests = complaintService.countMoreFeedbackRequestsByExerciseId(exercise.getId());
            long numberOfComplaints = complaintService.countComplaintsByExerciseId(exercise.getId());

            exercise.setNumberOfParticipations(numberOfSubmissions);
            exercise.setNumberOfAssessments(numberOfAssessments);
            exercise.setNumberOfComplaints(numberOfComplaints);
            exercise.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);
        }
        long end = System.currentTimeMillis();
        log.info("Finished /courses/" + courseId + "/with-exercises-and-relevant-participations call in " + (end - start) + "ms");
        return ResponseUtil.wrapOrNotFound(Optional.of(course));
    }

    /**
     * GET /courses/:courseId/stats-for-instructor-dashboard
     * <p>
     * A collection of useful statistics for the instructor course dashboard, including: - number of students - number of instructors - number of submissions - number of
     * assessments - number of complaints - number of open complaints - tutor leaderboard data
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     * @throws AccessForbiddenException if the current user doesn't have the permission to access the course
     */
    @GetMapping("/courses/{courseId}/stats-for-instructor-dashboard")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StatsForInstructorDashboardDTO> getStatsForInstructorDashboard(@PathVariable Long courseId) throws AccessForbiddenException {
        log.debug("REST request /courses/{courseId}/stats-for-instructor-dashboard");
        long start = System.currentTimeMillis();
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermission(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        StatsForInstructorDashboardDTO stats = new StatsForInstructorDashboardDTO();

        long numberOfComplaints = complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(courseId, ComplaintType.COMPLAINT);
        stats.setNumberOfComplaints(numberOfComplaints);
        long numberOfComplaintResponses = complaintResponseRepository.countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType(courseId,
                ComplaintType.COMPLAINT);
        stats.setNumberOfOpenComplaints(numberOfComplaints - numberOfComplaintResponses);

        long numberOfMoreFeedbackRequests = complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(courseId, ComplaintType.MORE_FEEDBACK);
        stats.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);
        long numberOfMoreFeedbackComplaintResponses = complaintResponseRepository.countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType(courseId,
                ComplaintType.MORE_FEEDBACK);
        stats.setNumberOfOpenMoreFeedbackRequests(numberOfMoreFeedbackRequests - numberOfMoreFeedbackComplaintResponses);

        stats.setNumberOfStudents(courseService.countNumberOfStudentsForCourse(course));

        long numberOfSubmissions = textSubmissionService.countSubmissionsToAssessByCourseId(courseId);
        numberOfSubmissions += modelingSubmissionService.countSubmissionsToAssessByCourseId(courseId);
        numberOfSubmissions += fileUploadSubmissionService.countSubmissionsToAssessByCourseId(courseId);
        numberOfSubmissions += programmingExerciseService.countSubmissionsToAssessByCourseId(courseId);

        stats.setNumberOfSubmissions(numberOfSubmissions);
        stats.setNumberOfAssessments(resultService.countNumberOfAssessments(courseId));

        long startT = System.currentTimeMillis();
        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getCourseLeaderboard(course);
        stats.setTutorLeaderboardEntries(leaderboardEntries);

        log.info("Finished TutorLeaderboard in " + (System.currentTimeMillis() - startT) + "ms");

        log.info("Finished /courses/" + courseId + "/stats-for-instructor-dashboard call in " + (System.currentTimeMillis() - start) + "ms");
        return ResponseEntity.ok(stats);
    }

    private boolean userHasPermission(Course course, User user) {
        return authCheckService.isTeachingAssistantInCourse(course, user) || authCheckService.isInstructorInCourse(course, user) || authCheckService.isAdmin();
    }

    /**
     * DELETE /courses/:courseId : delete the "id" course.
     *
     * @param courseId the id of the course to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> deleteCourse(@PathVariable long courseId) {
        log.info("REST request to delete Course : {}", courseId);
        Course course = courseService.findOneWithExercisesAndLectures(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (course == null) {
            return notFound();
        }
        for (Exercise exercise : course.getExercises()) {
            exerciseService.delete(exercise.getId(), false, false);
        }

        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User " + user.getLogin() + " has requested to delete the course {}", course.getTitle());

        for (Lecture lecture : course.getLectures()) {
            lectureService.delete(lecture);
        }

        List<GroupNotification> notifications = notificationService.findAllNotificationsForCourse(course);
        for (GroupNotification notification : notifications) {
            notificationService.deleteNotification(notification);
        }
        String title = course.getTitle();
        courseService.delete(courseId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, title)).build();
    }

    /**
     * GET /courses/:courseId/categories : Returns all categories used in a course
     *
     * @param courseId the id of the course to get the categories from
     * @return the ResponseEntity with status 200 (OK) and the list of categories or with status 404 (Not Found)
     */
    @GetMapping(value = "/courses/{courseId}/categories")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<String>> getCategoriesInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get categories of Course : {}", courseId);

        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = courseService.findOne(courseId);
        if (authCheckService.isAdmin() || authCheckService.isInstructorInCourse(course, user)) {
            // user can see this exercise
            Set<String> categories = exerciseService.findAllExerciseCategoriesForCourse(course);
            return ResponseEntity.ok().body(categories);
        }
        else {
            return forbidden();
        }
    }
}
