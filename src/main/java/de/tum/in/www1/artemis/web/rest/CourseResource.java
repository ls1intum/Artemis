package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.SHORT_NAME_PATTERN;
import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;
import static de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException.NOT_ALLOWED;
import static java.time.ZonedDateTime.now;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.exception.GroupAlreadyExistsException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.CIUserManagementService;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.web.util.ResponseUtil;

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

    @Value("${artemis.user-management.course-registration.allowed-username-pattern:#{null}}")
    private Optional<Pattern> allowedCourseRegistrationUsernamePattern;

    @Value("${artemis.course-archives-path}")
    private String courseArchivesDirPath;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final ExerciseService exerciseService;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final SubmissionService submissionService;

    private final ComplaintService complaintService;

    private final TutorLeaderboardService tutorLeaderboardService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AssessmentDashboardService assessmentDashboardService;

    private final Optional<VcsUserManagementService> optionalVcsUserManagementService;

    private final Optional<CIUserManagementService> optionalCiUserManagementService;

    private final Environment env;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final AuditEventRepository auditEventRepository;

    private final ExerciseRepository exerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final RatingService ratingService;

    public CourseResource(UserRepository userRepository, CourseService courseService, CourseRepository courseRepository, ExerciseService exerciseService,
            AuthorizationCheckService authCheckService, TutorParticipationRepository tutorParticipationRepository, Environment env,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository,
            SubmissionService submissionService, ComplaintService complaintService, TutorLeaderboardService tutorLeaderboardService,
            ProgrammingExerciseRepository programmingExerciseRepository, AuditEventRepository auditEventRepository, StudentParticipationRepository studentParticipationRepository,
            Optional<VcsUserManagementService> optionalVcsUserManagementService, AssessmentDashboardService assessmentDashboardService, ExerciseRepository exerciseRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, Optional<CIUserManagementService> optionalCiUserManagementService,
            ParticipantScoreRepository participantScoreRepository, RatingService ratingService) {
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.submissionService = submissionService;
        this.complaintService = complaintService;
        this.tutorLeaderboardService = tutorLeaderboardService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.optionalVcsUserManagementService = optionalVcsUserManagementService;
        this.optionalCiUserManagementService = optionalCiUserManagementService;
        this.auditEventRepository = auditEventRepository;
        this.env = env;
        this.assessmentDashboardService = assessmentDashboardService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.ratingService = ratingService;
    }

    /**
     * POST /courses : create a new course.
     *
     * @param course the course to create
     * @return the ResponseEntity with status 201 (Created) and with body the new course, or with status 400 (Bad Request) if the course has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Course> createCourse(@RequestBody Course course) throws URISyntaxException {
        log.debug("REST request to save Course : {}", course);
        if (course.getId() != null) {
            throw new BadRequestAlertException("A new course cannot already have an ID", ENTITY_NAME, "idexists");
        }

        validateShortName(course);

        List<Course> coursesWithSameShortName = courseRepository.findAllByShortName(course.getShortName());
        if (coursesWithSameShortName.size() > 0) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createAlert(applicationName, "A course with the same short name already exists. Please choose a different short name.", "shortnameAlreadyExists"))
                    .body(null);
        }

        validateRegistrationConfirmationMessage(course);
        validateComplaintsAndRequestMoreFeedbackConfig(course);
        validateOnlineCourseAndRegistrationEnabled(course);
        validateAccuracyOfScores(course);

        createOrValidateGroups(course);
        Course result = courseRepository.save(course);
        return ResponseEntity.created(new URI("/api/courses/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    private void createOrValidateGroups(Course course) {
        try {
            // We use default names if a group was not specified by the ADMIN.
            // NOTE: instructors cannot change the group of a course, because this would be a security issue!
            // only create default group names, if the ADMIN has used a custom group names, we assume that it already exists.

            if (!StringUtils.hasText(course.getStudentGroupName())) {
                course.setStudentGroupName(course.getDefaultStudentGroupName());
                artemisAuthenticationProvider.createGroup(course.getStudentGroupName());
            }
            else {
                checkIfGroupsExists(course.getStudentGroupName());
            }

            if (!StringUtils.hasText(course.getTeachingAssistantGroupName())) {
                course.setTeachingAssistantGroupName(course.getDefaultTeachingAssistantGroupName());
                artemisAuthenticationProvider.createGroup(course.getTeachingAssistantGroupName());
            }
            else {
                checkIfGroupsExists(course.getTeachingAssistantGroupName());
            }

            if (!StringUtils.hasText(course.getEditorGroupName())) {
                course.setEditorGroupName(course.getDefaultEditorGroupName());
                artemisAuthenticationProvider.createGroup(course.getEditorGroupName());
            }
            else {
                checkIfGroupsExists(course.getEditorGroupName());
            }

            if (!StringUtils.hasText(course.getInstructorGroupName())) {
                course.setInstructorGroupName(course.getDefaultInstructorGroupName());
                artemisAuthenticationProvider.createGroup(course.getInstructorGroupName());
            }
            else {
                checkIfGroupsExists(course.getInstructorGroupName());
            }
        }
        catch (GroupAlreadyExistsException ex) {
            throw new BadRequestAlertException(
                    ex.getMessage() + ": One of the groups already exists (in the external user management), because the short name was already used in Artemis before. "
                            + "Please choose a different short name!",
                    ENTITY_NAME, "shortNameWasAlreadyUsed", true);
        }
        catch (ArtemisAuthenticationException ex) {
            // a specified group does not exist, notify the client
            throw new BadRequestAlertException(ex.getMessage(), ENTITY_NAME, "groupNotFound", true);
        }
    }

    /**
     * PUT /courses : Updates an existing updatedCourse.
     *
     * @param updatedCourse the course to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated course
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Course> updateCourse(@RequestBody Course updatedCourse) throws URISyntaxException {
        log.debug("REST request to update Course : {}", updatedCourse);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (updatedCourse.getId() == null) {
            if (authCheckService.isAdmin(user)) {
                return createCourse(updatedCourse);
            }
            else {
                throw new AccessForbiddenException(NOT_ALLOWED);
            }
        }

        var existingCourse = courseRepository.findByIdElseThrow(updatedCourse.getId());
        if (!Objects.equals(existingCourse.getShortName(), updatedCourse.getShortName())) {
            throw new BadRequestAlertException("The course short name cannot be changed", ENTITY_NAME, "shortNameCannotChange", true);
        }

        // only allow admins or instructors of the existing course to change it
        // this is important, otherwise someone could put himself into the instructor group of the updated course
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, existingCourse, user);

        if (authCheckService.isAdmin(user)) {
            // if an admin changes a group, we need to check that the changed group exists
            try {
                if (!Objects.equals(existingCourse.getStudentGroupName(), updatedCourse.getStudentGroupName())) {
                    checkIfGroupsExists(updatedCourse.getStudentGroupName());
                }
                if (!Objects.equals(existingCourse.getTeachingAssistantGroupName(), updatedCourse.getTeachingAssistantGroupName())) {
                    checkIfGroupsExists(updatedCourse.getTeachingAssistantGroupName());
                }
                if (!Objects.equals(existingCourse.getEditorGroupName(), updatedCourse.getEditorGroupName())) {
                    checkIfGroupsExists(updatedCourse.getEditorGroupName());
                }
                if (!Objects.equals(existingCourse.getInstructorGroupName(), updatedCourse.getInstructorGroupName())) {
                    checkIfGroupsExists(updatedCourse.getInstructorGroupName());
                }
            }
            catch (ArtemisAuthenticationException ex) {
                // a specified group does not exist, notify the client
                throw new BadRequestAlertException(ex.getMessage(), ENTITY_NAME, "groupNotFound", true);
            }
        }
        else {
            // this means the user must be an instructor, who has NOT Admin rights.
            // instructors are not allowed to change group names, because this would lead to security problems

            if (!Objects.equals(existingCourse.getStudentGroupName(), updatedCourse.getStudentGroupName())) {
                throw new BadRequestAlertException("The student group name cannot be changed", ENTITY_NAME, "studentGroupNameCannotChange", true);
            }
            if (!Objects.equals(existingCourse.getTeachingAssistantGroupName(), updatedCourse.getTeachingAssistantGroupName())) {
                throw new BadRequestAlertException("The teaching assistant group name cannot be changed", ENTITY_NAME, "teachingAssistantGroupNameCannotChange", true);
            }
            if (!Objects.equals(existingCourse.getEditorGroupName(), updatedCourse.getEditorGroupName())) {
                throw new BadRequestAlertException("The editor group name cannot be changed", ENTITY_NAME, "editorGroupNameCannotChange", true);
            }
            if (!Objects.equals(existingCourse.getInstructorGroupName(), updatedCourse.getInstructorGroupName())) {
                throw new BadRequestAlertException("The instructor group name cannot be changed", ENTITY_NAME, "instructorGroupNameCannotChange", true);
            }
        }

        validateRegistrationConfirmationMessage(updatedCourse);
        validateComplaintsAndRequestMoreFeedbackConfig(updatedCourse);
        validateOnlineCourseAndRegistrationEnabled(updatedCourse);
        validateShortName(updatedCourse);
        validateAccuracyOfScores(updatedCourse);

        // Based on the old instructors, editors and TAs, we can update all exercises in the course in the VCS (if necessary)
        // We need the old instructors, editors and TAs, so that the VCS user management service can determine which
        // users no longer have TA, editor or instructor rights in the related exercise repositories.
        final var oldInstructorGroup = existingCourse.getInstructorGroupName();
        final var oldEditorGroup = existingCourse.getEditorGroupName();
        final var oldTeachingAssistantGroup = existingCourse.getTeachingAssistantGroupName();
        Course result = courseRepository.save(updatedCourse);
        optionalVcsUserManagementService
                .ifPresent(userManagementService -> userManagementService.updateCoursePermissions(result, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup));
        optionalCiUserManagementService
                .ifPresent(ciUserManagementService -> ciUserManagementService.updateCoursePermissions(result, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup));
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedCourse.getTitle())).body(result);
    }

    private void checkIfGroupsExists(String group) {
        if (!Arrays.asList(env.getActiveProfiles()).contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            return;
        }
        // only execute this check in the production environment because normal developers (while testing) might not have the right to call this method on the authentication server
        if (!artemisAuthenticationProvider.isGroupAvailable(group)) {
            throw new ArtemisAuthenticationException("Cannot save! The group " + group + " does not exist. Please double check the group name!");
        }
    }

    private void validateRegistrationConfirmationMessage(Course course) {
        if (course.getRegistrationConfirmationMessage() != null && course.getRegistrationConfirmationMessage().length() > 2000) {
            throw new BadRequestAlertException("Confirmation registration message must be shorter than 2000 characters", ENTITY_NAME, "confirmationRegistrationMessageInvalid",
                    true);
        }
    }

    private void validateComplaintsAndRequestMoreFeedbackConfig(Course course) {
        if (course.getMaxComplaints() == null) {
            // set the default value to prevent null pointer exceptions
            course.setMaxComplaints(3);
        }
        if (course.getMaxTeamComplaints() == null) {
            // set the default value to prevent null pointer exceptions
            course.setMaxTeamComplaints(3);
        }
        if (course.getMaxComplaints() < 0) {
            throw new BadRequestAlertException("Max Complaints cannot be negative", ENTITY_NAME, "maxComplaintsInvalid", true);
        }
        if (course.getMaxTeamComplaints() < 0) {
            throw new BadRequestAlertException("Max Team Complaints cannot be negative", ENTITY_NAME, "maxTeamComplaintsInvalid", true);
        }
        if (course.getMaxComplaintTimeDays() < 0) {
            throw new BadRequestAlertException("Max Complaint Days cannot be negative", ENTITY_NAME, "maxComplaintDaysInvalid", true);
        }
        if (course.getMaxRequestMoreFeedbackTimeDays() < 0) {
            throw new BadRequestAlertException("Max Request More Feedback Days cannot be negative", ENTITY_NAME, "maxRequestMoreFeedbackDaysInvalid", true);
        }
        if (course.getMaxComplaintTimeDays() == 0 && (course.getMaxComplaints() != 0 || course.getMaxTeamComplaints() != 0)) {
            throw new BadRequestAlertException("If complaints or more feedback requests are allowed, the complaint time in days must be positive.", ENTITY_NAME,
                    "complaintsConfigInvalid", true);
        }
        if (course.getMaxComplaintTimeDays() != 0 && course.getMaxComplaints() == 0 && course.getMaxTeamComplaints() == 0) {
            throw new BadRequestAlertException("If no complaints or more feedback requests are allowed, the complaint time in days should be set to zero.", ENTITY_NAME,
                    "complaintsConfigInvalid", true);
        }
    }

    private void validateOnlineCourseAndRegistrationEnabled(Course course) {
        if (course.isOnlineCourse() && course.isRegistrationEnabled()) {
            throw new BadRequestAlertException("Online course and registration enabled cannot be active at the same time", ENTITY_NAME, "onlineCourseRegistrationEnabledInvalid",
                    true);
        }
    }

    private void validateAccuracyOfScores(Course course) {
        if (course.getAccuracyOfScores() == null) {
            throw new BadRequestAlertException("The course needs to specify the accuracy of scores", ENTITY_NAME, "accuracyOfScoresNotSet", true);
        }
        if (course.getAccuracyOfScores() < 0 || course.getAccuracyOfScores() > 5) {
            throw new BadRequestAlertException("The accuracy of scores defined for the course is either negative or uses too many decimal places (more than five)", ENTITY_NAME,
                    "accuracyOfScoresInvalid", true);
        }
    }

    private void validateShortName(Course course) {
        // Check if course shortname matches regex
        Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(course.getShortName());
        if (!shortNameMatcher.matches()) {
            throw new BadRequestAlertException("The shortname is invalid", ENTITY_NAME, "shortnameInvalid", true);
        }
    }

    /**
     * POST /courses/{courseId}/register : Register for an existing course. This method registers the current user for the given course id in case the course has already started
     * and not finished yet. The user is added to the course student group in the Authentication System and the course student group is added to the user's groups in the Artemis
     * database.
     *
     * @param courseId to find the course
     * @return response entity for user who has been registered to the course
     */
    @PostMapping("/courses/{courseId}/register")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<User> registerForCourse(@PathVariable Long courseId) {
        Course course = courseRepository.findWithEagerOrganizationsElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthoritiesAndOrganizations();
        log.debug("REST request to register {} for Course {}", user.getName(), course.getTitle());
        if (allowedCourseRegistrationUsernamePattern.isPresent() && !allowedCourseRegistrationUsernamePattern.get().matcher(user.getLogin()).matches()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "registrationNotAllowed",
                    "Registration with this username is not allowed. Cannot register user")).body(null);
        }
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
        if (!Boolean.TRUE.equals(course.isRegistrationEnabled())) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "registrationDisabled", "The course does not allow registration. Cannot register user"))
                    .body(null);
        }
        if (course.getOrganizations() != null && course.getOrganizations().size() > 0 && !checkIfUserIsMemberOfCourseOrganizations(user, course)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "registrationNotAllowed",
                    "User is not member of any organization of this course. Cannot register user")).body(null);
        }
        courseService.registerUserForCourse(user, course);
        return ResponseEntity.ok(user);
    }

    /**
     * GET /courses : get all courses for administration purposes.
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return the list of courses (the user has access to)
     */
    @GetMapping("/courses")
    @PreAuthorize("hasRole('TA')")
    public List<Course> getAllCourses(@RequestParam(defaultValue = "false") boolean onlyActive) {
        log.debug("REST request to get all Courses the user has access to");
        User user = userRepository.getUserWithGroupsAndAuthorities();
        List<Course> courses = courseRepository.findAll();
        Stream<Course> userCourses = courses.stream().filter(course -> user.getGroups().contains(course.getTeachingAssistantGroupName())
                || user.getGroups().contains(course.getInstructorGroupName()) || authCheckService.isAdmin(user));
        if (onlyActive) {
            // only include courses that have NOT been finished
            userCourses = userCourses.filter(course -> course.getEndDate() == null || course.getEndDate().isAfter(ZonedDateTime.now()));
        }
        return userCourses.collect(Collectors.toList());
    }

    /**
     * GET /courses/courses-with-quiz : get all courses with quiz exercises for administration purposes.
     *
     * @return the list of courses
     */
    @GetMapping("/courses/courses-with-quiz")
    @PreAuthorize("hasRole('EDITOR')")
    public List<Course> getAllCoursesWithQuizExercises() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (authCheckService.isAdmin(user)) {
            return courseRepository.findAllWithQuizExercisesWithEagerExercises();
        }
        else {
            var userGroups = new ArrayList<>(user.getGroups());
            return courseRepository.getCoursesWithQuizExercisesForWhichUserHasAtLeastEditorAccess(userGroups);
        }
    }

    /**
     * GET /courses/with-user-stats : get all courses for administration purposes with user stats.
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return the list of courses (the user has access to)
     */
    @GetMapping("/courses/with-user-stats")
    @PreAuthorize("hasRole('TA')")
    public List<Course> getAllCoursesWithUserStats(@RequestParam(defaultValue = "false") boolean onlyActive) {
        log.debug("get courses with user stats, only active: {}", onlyActive);
        long start = System.currentTimeMillis();
        List<Course> courses = getAllCourses(onlyActive);
        for (Course course : courses) {
            course.setNumberOfInstructors(userRepository.countUserInGroup(course.getInstructorGroupName()));
            course.setNumberOfTeachingAssistants(userRepository.countUserInGroup(course.getTeachingAssistantGroupName()));
            course.setNumberOfEditors(userRepository.countUserInGroup(course.getEditorGroupName()));
            course.setNumberOfStudents(userRepository.countUserInGroup(course.getStudentGroupName()));
        }
        long end = System.currentTimeMillis();
        log.debug("getAllCoursesWithUserStats took {}ms for {} courses", end - start, courses.size());
        return courses;
    }

    /**
     * GET /courses/course-overview : get all courses for the management overview
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return a list of courses (the user has access to)
     */
    @GetMapping("/courses/course-management-overview")
    @PreAuthorize("hasRole('TA')")
    public List<Course> getAllCoursesForManagementOverview(@RequestParam(defaultValue = "false") boolean onlyActive) {
        return courseService.getAllCoursesForManagementOverview(onlyActive);
    }

    /**
     * GET /courses/to-register : get all courses that the current user can register to. Decided by the start and end date and if the registrationEnabled flag is set correctly
     *
     * @return the list of courses which are active
     */
    @GetMapping("/courses/to-register")
    @PreAuthorize("hasRole('USER')")
    public List<Course> getAllCoursesToRegister() {
        log.debug("REST request to get all currently active Courses that are not online courses");
        User user = userRepository.getUserWithGroupsAndAuthoritiesAndOrganizations();

        List<Course> allCoursesToRegister = courseRepository.findAllCurrentlyActiveNotOnlineAndRegistrationEnabledWithOrganizations();
        return allCoursesToRegister.stream().filter(course -> {
            // further, check if the course has been assigned to any organization and if yes,
            // check if user is member of at least one of them
            if (course.getOrganizations() != null && course.getOrganizations().size() > 0) {
                return checkIfUserIsMemberOfCourseOrganizations(user, course);
            }
            else {
                return true;
            }
        }).toList();
    }

    /**
     * GET /courses/{courseId}/for-dashboard
     *
     * @param courseId the courseId for which exercises and lectures should be fetched
     * @return a course wich all exercises and lectures visible to the student
     */
    @GetMapping("/courses/{courseId}/for-dashboard")
    @PreAuthorize("hasRole('USER')")
    public Course getCourseForDashboard(@PathVariable long courseId) {
        long start = System.currentTimeMillis();
        User user = userRepository.getUserWithGroupsAndAuthorities();

        Course course = courseService.findOneWithExercisesAndLecturesAndExamsForUser(courseId, user);
        fetchParticipationsWithSubmissionsAndResultsForCourses(List.of(course), user, start);
        return course;
    }

    /**
     * Note: The number of courses should not change
     *
     * @param courses           the courses for which the participations should be fetched
     * @param user              the user for which the participations should be fetched
     * @param startTimeInMillis start time for logging purposes
     */
    public void fetchParticipationsWithSubmissionsAndResultsForCourses(List<Course> courses, User user, long startTimeInMillis) {
        Set<Exercise> exercises = courses.stream().flatMap(course -> course.getExercises().stream()).collect(Collectors.toSet());
        List<StudentParticipation> participationsOfUserInExercises = studentParticipationRepository.getAllParticipationsOfUserInExercises(user, exercises);
        if (participationsOfUserInExercises.isEmpty()) {
            return;
        }
        for (Course course : courses) {
            boolean isStudent = !authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
            for (Exercise exercise : course.getExercises()) {
                // add participation with submission and result to each exercise
                exerciseService.filterForCourseDashboard(exercise, participationsOfUserInExercises, user.getLogin(), isStudent);
                // remove sensitive information from the exercise for students
                if (isStudent) {
                    exercise.filterSensitiveInformation();
                }
            }
        }
        Map<ExerciseMode, List<Exercise>> exercisesGroupedByExerciseMode = exercises.stream().collect(Collectors.groupingBy(Exercise::getMode));
        int noOfIndividualExercises = Optional.ofNullable(exercisesGroupedByExerciseMode.get(ExerciseMode.INDIVIDUAL)).orElse(List.of()).size();
        int noOfTeamExercises = Optional.ofNullable(exercisesGroupedByExerciseMode.get(ExerciseMode.TEAM)).orElse(List.of()).size();
        log.info("/courses/for-dashboard.done in {}ms for {} courses with {} individual exercises and {} team exercises for user {}",
                System.currentTimeMillis() - startTimeInMillis, courses.size(), noOfIndividualExercises, noOfTeamExercises, user.getLogin());
    }

    /**
     * GET /courses/for-dashboard
     *
     * @return the list of courses (the user has access to) including all exercises with participation and result for the user
     */
    @GetMapping("/courses/for-dashboard")
    @PreAuthorize("hasRole('USER')")
    public List<Course> getAllCoursesForDashboard() {
        long start = System.currentTimeMillis();
        log.debug("REST request to get all Courses the user has access to with exercises, participations and results");
        User user = userRepository.getUserWithGroupsAndAuthorities();

        // get all courses with exercises for this user
        List<Course> courses = courseService.findAllActiveWithExercisesAndLecturesAndExamsForUser(user);
        fetchParticipationsWithSubmissionsAndResultsForCourses(courses, user, start);
        return courses;
    }

    /**
     * GET /courses/for-notifications
     *
     * @return the list of courses (the user has access to)
     */
    @GetMapping("/courses/for-notifications")
    @PreAuthorize("hasRole('USER')")
    public List<Course> getAllCoursesForNotifications() {
        log.debug("REST request to get all Courses the user has access to");
        User user = userRepository.getUserWithGroupsAndAuthorities();

        return courseService.findAllActiveForUser(user);
    }

    /**
     * GET /courses/:courseId/for-assessment-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/for-assessment-dashboard")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Course> getCourseForAssessmentDashboard(@PathVariable long courseId) {
        log.debug("REST request /courses/{courseId}/for-assessment-dashboard");
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);

        Set<Exercise> interestingExercises = courseRepository.getInterestingExercisesForAssessmentDashboards(course.getExercises());
        course.setExercises(interestingExercises);
        List<TutorParticipation> tutorParticipations = tutorParticipationRepository.findAllByAssessedExercise_Course_IdAndTutor_Id(course.getId(), user.getId());
        assessmentDashboardService.generateStatisticsForExercisesForAssessmentDashboard(course.getExercises(), tutorParticipations, false);
        return ResponseUtil.wrapOrNotFound(Optional.of(course));
    }

    /**
     * GET /courses/:courseId/stats-for-assessment-dashboard A collection of useful statistics for the tutor course dashboard, including: - number of submissions to the course - number of
     * assessments - number of assessments assessed by the tutor - number of complaints
     *
     * all timestamps were measured when calling this method from the PGdP assessment-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/stats-for-assessment-dashboard")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<StatsForDashboardDTO> getStatsForAssessmentDashboard(@PathVariable long courseId) {
        long startRestCall = System.nanoTime();
        Course course = courseRepository.findByIdElseThrow(courseId);

        long start = System.currentTimeMillis();
        // 5ms - fast
        Set<Long> exerciseIdsOfCourse = exerciseRepository.findAllIdsByCourseId(courseId);
        long end = System.currentTimeMillis();
        log.info("Finished > exerciseRepository.findAllIdsByCourseId < call for course {} in {}ms", course.getId(), end - start);

        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        StatsForDashboardDTO stats = new StatsForDashboardDTO();
        start = System.currentTimeMillis();
        // 2.2s - slow
        long numberOfInTimeSubmissions = submissionRepository.countAllByExerciseIdsSubmittedBeforeDueDate(exerciseIdsOfCourse);
        end = System.currentTimeMillis();
        log.info("Finished > submissionRepository.countByCourseIdSubmittedBeforeDueDate< call for course {} in {}ms", course.getId(), end - start);

        start = System.currentTimeMillis();
        // 2.3s - slow
        numberOfInTimeSubmissions += programmingExerciseRepository.countAllSubmissionsByExerciseIdsSubmitted(exerciseIdsOfCourse);
        end = System.currentTimeMillis();
        log.info("Finished > programmingExerciseRepository.countSubmissionsByCourseIdSubmitted< call for course {} in {}ms", course.getId(), end - start);

        start = System.currentTimeMillis();
        // 3.0s - slow
        final long numberOfLateSubmissions = submissionRepository.countAllByExerciseIdsSubmittedAfterDueDate(exerciseIdsOfCourse);
        end = System.currentTimeMillis();
        log.info("Finished > submissionRepository.countByCourseIdSubmittedAfterDueDate< call for course {} in {}ms", course.getId(), end - start);

        start = System.currentTimeMillis();
        // 3.8s - very slow
        DueDateStat totalNumberOfAssessments = resultRepository.countNumberOfAssessments(exerciseIdsOfCourse);
        end = System.currentTimeMillis();
        log.info("Finished > resultRepository.countNumberOfAssessments < call for course {} in {}ms", course.getId(), end - start);

        stats.setTotalNumberOfAssessments(totalNumberOfAssessments);

        // no examMode here, so its the same as totalNumberOfAssessments
        DueDateStat[] numberOfAssessmentsOfCorrectionRounds = { totalNumberOfAssessments };
        stats.setNumberOfAssessmentsOfCorrectionRounds(numberOfAssessmentsOfCorrectionRounds);

        stats.setNumberOfSubmissions(new DueDateStat(numberOfInTimeSubmissions, numberOfLateSubmissions));

        start = System.currentTimeMillis();
        // 0.14s - a bit slow
        final long numberOfMoreFeedbackRequests = complaintService.countMoreFeedbackRequestsByCourseId(courseId);
        stats.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);
        end = System.currentTimeMillis();
        log.info("Finished >  complaintService.countMoreFeedbackRequestsByCourseId < call for course {} in {}ms", course.getId(), end - start);

        start = System.currentTimeMillis();
        final long numberOfMoreFeedbackComplaintResponses = complaintService.countMoreFeedbackRequestResponsesByCourseId(courseId);
        stats.setNumberOfOpenMoreFeedbackRequests(numberOfMoreFeedbackRequests - numberOfMoreFeedbackComplaintResponses);
        end = System.currentTimeMillis();
        log.info("Finished > complaintService.countMoreFeedbackRequestResponsesByCourseId < call for course {} in {}ms", course.getId(), end - start);

        start = System.currentTimeMillis();
        // 0.12s - a bit slow
        final long numberOfComplaints = complaintService.countComplaintsByCourseId(courseId);
        end = System.currentTimeMillis();
        log.info("Finished >  complaintService.countComplaintsByCourseId < call for course {} in {}ms", course.getId(), end - start);

        stats.setNumberOfComplaints(numberOfComplaints);

        start = System.currentTimeMillis();
        final long numberOfComplaintResponses = complaintService.countComplaintResponsesByCourseId(courseId);
        stats.setNumberOfOpenComplaints(numberOfComplaints - numberOfComplaintResponses);
        end = System.currentTimeMillis();
        log.info("Finished > complaintService.countComplaintResponsesByCourseId < call for course {} in {}ms", course.getId(), end - start);

        start = System.currentTimeMillis();
        // 10ms - fast
        final long numberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByUserIdAndCourseId(userRepository.getUserWithGroupsAndAuthorities().getId(), courseId);
        end = System.currentTimeMillis();
        log.info("Finished > submissionRepository.countLockedSubmissionsByUserIdAndCourseId < call for course {} in {}ms", course.getId(), end - start);

        stats.setNumberOfAssessmentLocks(numberOfAssessmentLocks);

        start = System.currentTimeMillis();
        // 10ms - fast
        final long totalNumberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByCourseId(courseId);
        end = System.currentTimeMillis();
        log.info("Finished > submissionRepository.countLockedSubmissionsByCourseId < call for course {} in {}ms", course.getId(), end - start);
        stats.setTotalNumberOfAssessmentLocks(totalNumberOfAssessmentLocks);

        start = System.currentTimeMillis();
        // 8s - very slow
        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getCourseLeaderboard(course, exerciseIdsOfCourse);
        end = System.currentTimeMillis();
        log.info("Finished > tutorLeaderboardService.getCourseLeaderboard < call for course {} in {}ms", course.getId(), end - start);

        stats.setTutorLeaderboardEntries(leaderboardEntries);

        start = System.currentTimeMillis();
        // 9ms - fast
        stats.setNumberOfRatings(ratingService.countRatingsByCourse(courseId));
        end = System.currentTimeMillis();
        log.info("Finished > ratingService.countRatingsByCourse < call for course {} in {}ms", course.getId(), end - start);

        log.info("Finished > getStatsForAssessmentDashboard < call for course {} in {}", course.getId(), TimeLogUtil.formatDurationFrom(startRestCall));
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /courses/:courseId : get the "id" course.
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Course> getCourse(@PathVariable Long courseId) {
        log.debug("REST request to get Course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        course.setNumberOfInstructors(userRepository.countUserInGroup(course.getInstructorGroupName()));
        course.setNumberOfTeachingAssistants(userRepository.countUserInGroup(course.getTeachingAssistantGroupName()));
        course.setNumberOfEditors(userRepository.countUserInGroup(course.getEditorGroupName()));
        course.setNumberOfStudents(userRepository.countUserInGroup(course.getStudentGroupName()));
        return ResponseUtil.wrapOrNotFound(Optional.of(course));
    }

    /**
     * GET /courses/:courseId : get the "id" course.
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/with-exercises")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Course> getCourseWithExercises(@PathVariable Long courseId) {
        log.debug("REST request to get Course : {}", courseId);
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    /**
     * GET /courses/:courseId/with-exercises-and-relevant-participations Get the "id" course, with text and modelling exercises and their participations It can be used only by
     * instructors for the instructor dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/with-exercises-and-relevant-participations")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Course> getCourseWithExercisesAndRelevantParticipations(@PathVariable Long courseId) {
        log.debug("REST request to get Course with exercises and relevant participations : {}", courseId);
        long start = System.currentTimeMillis();
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        Set<Exercise> interestingExercises = courseRepository.getInterestingExercisesForAssessmentDashboards(course.getExercises());
        course.setExercises(interestingExercises);

        for (Exercise exercise : interestingExercises) {

            DueDateStat numberOfSubmissions;
            DueDateStat totalNumberOfAssessments;

            if (exercise instanceof ProgrammingExercise) {
                numberOfSubmissions = new DueDateStat(programmingExerciseRepository.countLegalSubmissionsByExerciseIdSubmitted(exercise.getId(), false), 0L);
                totalNumberOfAssessments = new DueDateStat(programmingExerciseRepository.countAssessmentsByExerciseIdSubmitted(exercise.getId(), false), 0L);
            }
            else {
                numberOfSubmissions = submissionRepository.countSubmissionsForExercise(exercise.getId(), false);
                totalNumberOfAssessments = resultRepository.countNumberOfFinishedAssessmentsForExercise(exercise.getId(), false);
            }

            exercise.setNumberOfSubmissions(numberOfSubmissions);
            exercise.setTotalNumberOfAssessments(totalNumberOfAssessments);

            final long numberOfMoreFeedbackRequests = complaintService.countMoreFeedbackRequestsByExerciseId(exercise.getId());
            final long numberOfComplaints = complaintService.countComplaintsByExerciseId(exercise.getId());

            exercise.setNumberOfComplaints(numberOfComplaints);
            exercise.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);
        }
        long end = System.currentTimeMillis();
        log.info("Finished /courses/{}/with-exercises-and-relevant-participations call in {}ms", courseId, end - start);
        return ResponseUtil.wrapOrNotFound(Optional.of(course));
    }

    /**
     * GET /courses/:courseId/with-organizations Get a course by id with eagerly loaded organizations
     * @param courseId the id of the course
     * @return the course with eagerly loaded organizations
     */
    @GetMapping("/courses/{courseId}/with-organizations")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Course> getCourseWithOrganizations(@PathVariable Long courseId) {
        log.debug("REST request to get a course with its organizations : {}", courseId);
        Course course = courseRepository.findWithEagerOrganizationsElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    /**
     * GET /courses/:courseId/lockedSubmissions Get locked submissions for course for user
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/lockedSubmissions")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Submission>> getLockedSubmissionsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all locked submissions for course : {}", courseId);
        long start = System.currentTimeMillis();
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);

        List<Submission> submissions = submissionService.getLockedSubmissions(courseId);

        for (Submission submission : submissions) {
            submissionService.hideDetails(submission, user);
        }

        long end = System.currentTimeMillis();
        log.debug("Finished /courses/{}/submissions call in {}ms", courseId, end - start);
        return ResponseEntity.ok(submissions);
    }

    /**
     * GET /courses/exercises-for-management-overview
     *
     * gets the courses with exercises for the user
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return ResponseEntity with status, containing a list of courses
     */
    @GetMapping("/courses/exercises-for-management-overview")
    @PreAuthorize("hasRole('TA')")
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
     *
     * gets the statistics for the courses of the user
     * statistics for exercises with an assessment due date (or due date if there is no assessment due date)
     * in the past are limited to the five most recent
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return ResponseEntity with status, containing a list of <code>CourseManagementOverviewStatisticsDTO</code>
     */
    @GetMapping("/courses/stats-for-management-overview")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<CourseManagementOverviewStatisticsDTO>> getExerciseStatsForCourseOverview(@RequestParam(defaultValue = "false") boolean onlyActive) {
        final List<CourseManagementOverviewStatisticsDTO> courseDTOs = new ArrayList<>();
        for (final var course : courseService.getAllCoursesForManagementOverview(onlyActive)) {
            final var courseId = course.getId();
            final var courseDTO = new CourseManagementOverviewStatisticsDTO();
            courseDTO.setCourseId(courseId);

            var studentsGroup = courseRepository.findStudentGroupName(courseId);
            var amountOfStudentsInCourse = Math.toIntExact(userRepository.countUserInGroup(studentsGroup));
            courseDTO.setExerciseDTOS(exerciseService.getStatisticsForCourseManagementOverview(courseId, amountOfStudentsInCourse));

            var exerciseIds = exerciseRepository.findAllIdsByCourseId(courseId);
            courseDTO.setActiveStudents(courseService.getActiveStudents(exerciseIds, 0, 4));
            courseDTOs.add(courseDTO);
        }

        return ResponseEntity.ok(courseDTOs);
    }

    /**
     * DELETE /courses/:courseId : delete the "id" course.
     *
     * @param courseId the id of the course to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCourse(@PathVariable long courseId) {
        log.info("REST request to delete Course : {}", courseId);
        Course course = courseRepository.findWithEagerExercisesAndLecturesAndLectureUnitsAndLearningGoalsById(courseId);
        if (course == null) {
            throw new EntityNotFoundException("Course", courseId);
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has requested to delete the course {}", user.getLogin(), course.getTitle());

        courseService.delete(course);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, course.getTitle())).build();
    }

    /**
     * PUT /courses/{courseId} : archive an existing course asynchronously. This method starts the process of archiving all course exercises, submissions and results in a large
     * zip file. It immediately returns and runs this task asynchronously. When the task is done, the course is marked as archived, which means the zip file can be downloaded.
     *
     * @param courseId the id of the course
     * @return empty
     */
    @PutMapping("/courses/{courseId}/archive")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> archiveCourse(@PathVariable Long courseId) {
        log.info("REST request to archive Course : {}", courseId);
        final Course course = courseRepository.findByIdWithExercisesAndLecturesElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        // Archiving a course is only possible after the course is over
        if (now().isBefore(course.getEndDate())) {
            throw new BadRequestAlertException("You cannot archive a course that is not over.", ENTITY_NAME, "courseNotOver", true);
        }
        courseService.archiveCourse(course);

        // Note: in the first version, we do not store the results with feedback and other meta data, as those will stay available in Artemis, the main focus is to allow
        // instructors to download student repos in order to delete those on Bitbucket/Gitlab

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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @GetMapping("/courses/{courseId}/download-archive")
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
        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }

    /**
     * DELETE /courses/:course/cleanup : Cleans up a course by deleting all student submissions.
     *
     * @param courseId Id of the course to clean up
     * @return ResponseEntity with status
     */
    @DeleteMapping("/courses/{courseId}/cleanup")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Resource> cleanup(@PathVariable Long courseId) {
        log.info("REST request to cleanup the Course : {}", courseId);
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        // Forbid cleaning the course if no archive has been created
        if (!course.hasCourseArchive()) {
            throw new BadRequestAlertException("Failed to clean up course " + courseId + " because it needs to be archived first.", ENTITY_NAME, "archivenonexistant");
        }
        courseService.cleanupCourse(courseId);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/:courseId/categories : Returns all categories used in a course
     *
     * @param courseId the id of the course to get the categories from
     * @return the ResponseEntity with status 200 (OK) and the list of categories or with status 404 (Not Found)
     */
    @GetMapping(value = "/courses/{courseId}/categories")
    @PreAuthorize("hasRole('EDITOR')")
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
    @GetMapping(value = "/courses/{courseId}/students")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<User>> getAllStudentsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all students in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return getAllUsersInGroup(course, course.getStudentGroupName());
    }

    /**
     * GET /courses/:courseId/tutors : Returns all users that belong to the tutor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping(value = "/courses/{courseId}/tutors")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<User>> getAllTutorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all tutors in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return getAllUsersInGroup(course, course.getTeachingAssistantGroupName());
    }

    /**
     * GET /courses/:courseId/editors : Returns all users that belong to the editor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping(value = "/courses/{courseId}/editors")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<User>> getAllEditorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all editors in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return getAllUsersInGroup(course, course.getEditorGroupName());
    }

    /**
     * GET /courses/:courseId/instructors : Returns all users that belong to the instructor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping(value = "/courses/{courseId}/instructors")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<User>> getAllInstructorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all instructors in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return getAllUsersInGroup(course, course.getInstructorGroupName());
    }

    /**
     * GET /courses/:courseId/title : Returns the title of the course with the given id
     *
     * @param courseId the id of the course
     * @return the title of the course wrapped in an ResponseEntity or 404 Not Found if no course with that id exists
     */
    @GetMapping(value = "/courses/{courseId}/title")
    @PreAuthorize("hasRole('USER')")
    @ResponseBody
    public ResponseEntity<String> getCourseTitle(@PathVariable Long courseId) {
        final var title = courseRepository.getCourseTitle(courseId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * Returns all users in a course that belong to the given group
     *
     * @param course    the course
     * @param groupName the name of the group
     * @return list of users
     */
    @NotNull
    public ResponseEntity<List<User>> getAllUsersInGroup(Course course, String groupName) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        var usersInGroup = userRepository.findAllInGroup(groupName);
        usersInGroup.forEach(user -> {
            // remove some values which are not needed in the client
            user.setLastNotificationRead(null);
            user.setActivationKey(null);
            user.setLangKey(null);
            user.setLastNotificationRead(null);
            user.setLastModifiedBy(null);
            user.setLastModifiedDate(null);
            user.setCreatedBy(null);
            user.setCreatedDate(null);
        });
        return ResponseEntity.ok().body(usersInGroup);
    }

    /**
     * Post /courses/:courseId/students/:studentLogin : Add the given user to the students of the course so that the student can access the course
     *
     * @param courseId     the id of the course
     * @param studentLogin the login of the user who should get student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
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
    @PostMapping(value = "/courses/{courseId}/tutors/{tutorLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> addTutorToCourse(@PathVariable Long courseId, @PathVariable String tutorLogin) {
        log.debug("REST request to add {} as tutors to course : {}", tutorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return addUserToCourseGroup(tutorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getTeachingAssistantGroupName(), Role.TEACHING_ASSISTANT);
    }

    /**
     * Post /courses/:courseId/editors/:editorLogin : Add the given user to the editors of the course so that the student can access the course administration
     *
     * @param courseId   the id of the course
     * @param editorLogin the login of the user who should get editor access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/editors/{editorLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> addEditorToCourse(@PathVariable Long courseId, @PathVariable String editorLogin) {
        log.debug("REST request to add {} as editors to course : {}", editorLogin, courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);

        // Courses that have been created before Artemis version 4.11.9 do not have an editor group.
        // The editor group would be need to be set manually by instructors for the course and manually added to Jira.
        // To increase the usability the group is automatically generated when a user is added.
        if (!StringUtils.hasText(course.getEditorGroupName())) {
            try {
                course.setEditorGroupName(course.getDefaultEditorGroupName());
                if (!artemisAuthenticationProvider.isGroupAvailable(course.getDefaultEditorGroupName())) {
                    artemisAuthenticationProvider.createGroup(course.getDefaultEditorGroupName());
                }
            }
            catch (GroupAlreadyExistsException ex) {
                throw new BadRequestAlertException(
                        ex.getMessage() + ": One of the groups already exists (in the external user management), because the short name was already used in Artemis before. "
                                + "Please choose a different short name!",
                        ENTITY_NAME, "shortNameWasAlreadyUsed", true);
            }
            catch (ArtemisAuthenticationException ex) {
                // a specified group does not exist, notify the client
                throw new BadRequestAlertException(ex.getMessage(), ENTITY_NAME, "groupNotFound", true);
            }
            courseRepository.save(course);
        }

        return addUserToCourseGroup(editorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getEditorGroupName(), Role.EDITOR);
    }

    /**
     * Post /courses/:courseId/instructors/:instructorLogin : Add the given user to the instructors of the course so that the student can access the course administration
     *
     * @param courseId        the id of the course
     * @param instructorLogin the login of the user who should get instructors access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/instructors/{instructorLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
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
            courseService.addUserToGroup(userToAddToGroup.get(), group, role);
            return ResponseEntity.ok().body(null);
        }
        else {
            throw new AccessForbiddenException(NOT_ALLOWED);
        }
    }

    /**
     * DELETE /courses/:courseId/students/:studentLogin : Remove the given user from the students of the course so that the student cannot access the course any more
     *
     * @param courseId     the id of the course
     * @param studentLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeStudentFromCourse(@PathVariable Long courseId, @PathVariable String studentLogin) {
        log.debug("REST request to remove {} as student from course : {}", studentLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(studentLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getStudentGroupName(), Role.STUDENT);
    }

    /**
     * DELETE /courses/:courseId/tutors/:tutorsLogin : Remove the given user from the tutors of the course so that the tutors cannot access the course administration any more
     *
     * @param courseId   the id of the course
     * @param tutorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/tutors/{tutorLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeTutorFromCourse(@PathVariable Long courseId, @PathVariable String tutorLogin) {
        log.debug("REST request to remove {} as tutor from course : {}", tutorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(tutorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getTeachingAssistantGroupName(), Role.TEACHING_ASSISTANT);
    }

    /**
     * DELETE /courses/:courseId/editors/:editorsLogin : Remove the given user from the editors of the course so that the editors cannot access the course administration any more
     *
     * @param courseId   the id of the course
     * @param editorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/editors/{editorLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeEditorFromCourse(@PathVariable Long courseId, @PathVariable String editorLogin) {
        log.debug("REST request to remove {} as editor from course : {}", editorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(editorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getEditorGroupName(), Role.EDITOR);
    }

    /**
     * DELETE /courses/:courseId/instructors/:instructorLogin : Remove the given user from the instructors of the course so that the instructor cannot access the course administration any more
     *
     * @param courseId        the id of the course
     * @param instructorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/instructors/{instructorLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeInstructorFromCourse(@PathVariable Long courseId, @PathVariable String instructorLogin) {
        log.debug("REST request to remove {} as instructor from course : {}", instructorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(instructorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getInstructorGroupName(), Role.INSTRUCTOR);
    }

    /**
     * removes the userLogin from the group (student, tutors or instructors) of the given course
     *
     * @param userLogin         the user login of the student, tutor or instructor who should be removed from the group
     * @param instructorOrAdmin the user who initiates this request who must be an instructor of the given course or an admin
     * @param course            the course which is only passes to check if the instructorOrAdmin is an instructor of the course
     * @param group             the group from which the userLogin should be removed
     * @param role              the role which should be removed
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found) or with status 403 (Forbidden)
     */
    @NotNull
    public ResponseEntity<Void> removeUserFromCourseGroup(String userLogin, User instructorOrAdmin, Course course, String group, Role role) {
        if (authCheckService.isAtLeastInstructorInCourse(course, instructorOrAdmin)) {
            Optional<User> userToRemoveFromGroup = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userLogin);
            if (userToRemoveFromGroup.isEmpty()) {
                throw new EntityNotFoundException("User", userLogin);
            }
            courseService.removeUserFromGroup(userToRemoveFromGroup.get(), group, role);
            return ResponseEntity.ok().body(null);
        }
        else {
            throw new AccessForbiddenException(NOT_ALLOWED);
        }
    }

    /**
     * Utility method used to check whether a user is member of at least one organization of a given course
     * @param user the user to check
     * @param course the course to check
     * @return true if the user is member of at least one organization of the course. false otherwise
     */
    private boolean checkIfUserIsMemberOfCourseOrganizations(User user, Course course) {
        boolean isMember = false;
        for (Organization organization : courseRepository.findWithEagerOrganizationsElseThrow(course.getId()).getOrganizations()) {
            if (user.getOrganizations().contains(organization)) {
                isMember = true;
                break;
            }
        }
        return isMember;
    }

    /**
     * GET /courses/{courseId}/management-detail : Gets the data needed for the course management detail view
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the body, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/management-detail")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<CourseManagementDetailViewDTO> getCourseDTOForDetailView(@PathVariable Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        Set<Exercise> exercises = exerciseRepository.findAllExercisesByCourseId(courseId);
        // For the average score we need to only consider scores which are included completely or as bonus
        Set<Exercise> includedExercises = exercises.stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(Collectors.toSet());
        Double averageScoreForCourse = participantScoreRepository.findAvgScore(includedExercises);
        averageScoreForCourse = averageScoreForCourse != null ? averageScoreForCourse : 0.0;
        double reachablePoints = includedExercises.stream().map(Exercise::getMaxPoints).collect(Collectors.toSet()).stream().mapToDouble(Double::doubleValue).sum();

        Set<Long> exerciseIdsOfCourse = exercises.stream().map(Exercise::getId).collect(Collectors.toSet());
        CourseManagementDetailViewDTO dto = courseService.getStatsForDetailView(courseId, exerciseIdsOfCourse);

        setAssessments(dto, exerciseIdsOfCourse);
        setComplaints(dto, courseId);
        setMoreFeedbackRequests(dto, courseId);
        setAverageScore(dto, reachablePoints, averageScoreForCourse, course);

        return ResponseEntity.ok(dto);
    }

    /**
     *  Helper method for setting the assessments in the CourseManagementDetailViewDTO
     *  Only counting assessments and submissions which are handed in in time
     */
    private void setAssessments(CourseManagementDetailViewDTO dto, Set<Long> exerciseIdsOfCourse) {
        DueDateStat assessments = resultRepository.countNumberOfAssessments(exerciseIdsOfCourse);
        long numberOfAssessments = assessments.inTime() + assessments.late();
        dto.setCurrentAbsoluteAssessments(numberOfAssessments);

        long numberOfInTimeSubmissions = submissionRepository.countAllByExerciseIdsSubmittedBeforeDueDate(exerciseIdsOfCourse)
                + programmingExerciseRepository.countAllSubmissionsByExerciseIdsSubmitted(exerciseIdsOfCourse);
        long numberOfLateSubmissions = submissionRepository.countAllByExerciseIdsSubmittedAfterDueDate(exerciseIdsOfCourse);

        long numberOfSubmissions = numberOfInTimeSubmissions + numberOfLateSubmissions;
        dto.setCurrentMaxAssessments(numberOfSubmissions);
        dto.setCurrentPercentageAssessments(calculatePercentage(numberOfAssessments, numberOfSubmissions));
    }

    /**
     *  Helper method for setting the complaints in the CourseManagementDetailViewDTO
     */
    private void setComplaints(CourseManagementDetailViewDTO dto, Long courseId) {
        long numberOfAnsweredComplaints = complaintResponseRepository
                .countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(courseId, ComplaintType.COMPLAINT);
        dto.setCurrentAbsoluteComplaints(numberOfAnsweredComplaints);
        long numberOfComplaints = complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(courseId, ComplaintType.COMPLAINT);
        dto.setCurrentMaxComplaints(numberOfComplaints);
        dto.setCurrentPercentageComplaints(calculatePercentage(numberOfAnsweredComplaints, numberOfComplaints));
    }

    /**
     *  Helper method for setting the more feedback requests in the CourseManagementDetailViewDTO
     */
    private void setMoreFeedbackRequests(CourseManagementDetailViewDTO dto, Long courseId) {
        long numberOfAnsweredFeedbackRequests = complaintResponseRepository
                .countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(courseId, ComplaintType.MORE_FEEDBACK);
        dto.setCurrentAbsoluteMoreFeedbacks(numberOfAnsweredFeedbackRequests);
        long numberOfMoreFeedbackRequests = complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(courseId, ComplaintType.MORE_FEEDBACK);
        dto.setCurrentMaxMoreFeedbacks(numberOfMoreFeedbackRequests);
        dto.setCurrentPercentageMoreFeedbacks(calculatePercentage(numberOfAnsweredFeedbackRequests, numberOfMoreFeedbackRequests));
    }

    /**
     *  Helper method for setting the average score in the CourseManagementDetailViewDTO
     */
    private void setAverageScore(CourseManagementDetailViewDTO dto, double reachablePoints, Double averageScoreForCourse, Course course) {
        dto.setCurrentMaxAverageScore(reachablePoints);
        dto.setCurrentAbsoluteAverageScore(roundScoreSpecifiedByCourseSettings((averageScoreForCourse / 100.0) * reachablePoints, course));
        if (reachablePoints > 0.0) {
            dto.setCurrentPercentageAverageScore(roundScoreSpecifiedByCourseSettings(averageScoreForCourse, course));
        }
        else {
            dto.setCurrentPercentageAverageScore(0.0);
        }
    }

    private double calculatePercentage(double positive, double total) {
        return total > 0.0 ? Math.round(positive * 1000.0 / total) / 10.0 : 0.0;
    }

    /**
     * GET /courses/:courseId/statistics : Get the active students for this particular course
     *
     * @param courseId the id of the course
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/statistics")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Integer[]> getActiveStudentsForCourseDetailView(@PathVariable Long courseId, @RequestParam Long periodIndex) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, courseRepository.findByIdElseThrow(courseId), null);
        var exerciseIds = exerciseRepository.findAllIdsByCourseId(courseId);
        return ResponseEntity.ok(courseService.getActiveStudents(exerciseIds, periodIndex, 16));
    }

    /**
     * POST /courses/:courseId/:courseGroup : Add multiple users to the user group of the course so that they can access the course
     * The passed list of UserDTOs must include the registration number (the other entries are currently ignored and can be left out)
     * Note: registration based on other user attributes (e.g. email, name, login) is currently NOT supported
     *
     * This method first tries to find the student in the internal Artemis user database (because the user is most probably already using Artemis).
     * In case the user cannot be found, we additionally search the (TUM) LDAP in case it is configured properly.
     *
     * @param courseId      the id of the course
     * @param studentDtos   the list of students (with at least registration number) who should get access to the course
     * @param courseGroup   the group, the user has to be added to, either 'students', 'tutors', 'instructors' or 'editors'
     * @return the list of students who could not be registered for the course, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    @PostMapping("courses/{courseId}/{courseGroup}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<StudentDTO>> addUsersToCourseGroup(@PathVariable Long courseId, @PathVariable String courseGroup, @RequestBody List<StudentDTO> studentDtos) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, courseRepository.findByIdElseThrow(courseId), null);
        log.debug("REST request to add {} as {} to course {}", studentDtos, courseGroup, courseId);
        List<StudentDTO> notFoundStudentsDtos = courseService.registerUsersForCourseGroup(courseId, studentDtos, courseGroup);
        return ResponseEntity.ok().body(notFoundStudentsDtos);
    }
}
