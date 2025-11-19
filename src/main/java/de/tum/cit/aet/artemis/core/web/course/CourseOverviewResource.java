package de.tum.cit.aet.artemis.core.web.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.service.ComplaintService;
import de.tum.cit.aet.artemis.assessment.service.CourseScoreCalculationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.core.dto.CoursesForDashboardDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizQuestionProgressService;

/**
 * REST controller for providing courses in the student view.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/")
public class CourseOverviewResource {

    private static final String ENTITY_NAME = "course";

    private static final String COMPLAINT_ENTITY_NAME = "complaint";

    private static final Logger log = LoggerFactory.getLogger(CourseOverviewResource.class);

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final CourseScoreCalculationService courseScoreCalculationService;

    private final ComplaintService complaintService;

    private final QuizQuestionProgressService quizQuestionProgressService;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final GradingScaleRepository gradingScaleRepository;

    private final TeamRepository teamRepository;

    public CourseOverviewResource(UserRepository userRepository, CourseService courseService, CourseRepository courseRepository, AuthorizationCheckService authCheckService,
            CourseScoreCalculationService courseScoreCalculationService, GradingScaleRepository gradingScaleRepository, Optional<ExamRepositoryApi> examRepositoryApi,
            ComplaintService complaintService, TeamRepository teamRepository, QuizQuestionProgressService quizQuestionProgressService) {
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.courseScoreCalculationService = courseScoreCalculationService;
        this.gradingScaleRepository = gradingScaleRepository;
        this.examRepositoryApi = examRepositoryApi;
        this.complaintService = complaintService;
        this.teamRepository = teamRepository;
        this.quizQuestionProgressService = quizQuestionProgressService;
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
        boolean trainingEnabled = quizQuestionProgressService.questionsAvailableForTraining(courseId);
        course.setTrainingEnabled(trainingEnabled);
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
        Set<Course> courses = courseService.findAllActiveWithExercisesForUser(user);
        log.debug("courseService.findAllActiveWithExercisesForUser done");
        courseService.fetchParticipationsWithSubmissionsAndResultsForCourses(courses, user, false);

        log.debug("courseService.fetchParticipationsWithSubmissionsAndResultsForCourses done");

        // we explicitly add 1 hour here to compensate for potential write extensions. Calculating it exactly is not feasible here
        Set<Exam> activeExams;
        if (examRepositoryApi.isPresent()) {
            activeExams = examRepositoryApi.get().findActiveExams(courses.stream().map(Course::getId).collect(Collectors.toSet()), user.getId(), ZonedDateTime.now(),
                    ZonedDateTime.now().plusHours(1));
        }
        else {
            activeExams = Set.of();
        }
        Set<CourseForDashboardDTO> coursesForDashboard = new HashSet<>();
        for (Course course : courses) {
            // Passing null here for the grading scale is fine. This only leads to presentation scores not being considered which doesn't matter for the dashboard.
            // Not fetching plagiarism cases before the calculation also affects calculation as plagiarism cases are not considered in the scores, but this is fine for the
            // dashboard.
            // We prefer to have better performance for 99.9% of the users without plagiarism cases over accurate scores for the 0.1% of users with plagiarism cases.
            CourseForDashboardDTO courseForDashboardDTO = courseScoreCalculationService.getScoresAndParticipationResults(course, null, user.getId(), false);
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
     * GET /courses/:courseId : get the "id" course.
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    // TODO: this method is invoked quite often as part of course management resolve. However, it might not be necessary to fetch tutorial group configuration and online course
    // configuration in such cases.
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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseDropdownDTO(Long id, String title, String courseIcon) {
    }
}
