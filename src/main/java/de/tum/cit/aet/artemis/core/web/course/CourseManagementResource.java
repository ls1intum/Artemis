package de.tum.cit.aet.artemis.core.web.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentDashboardService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseExistingExerciseDetailsDTO;
import de.tum.cit.aet.artemis.core.dto.CourseForImportDTO;
import de.tum.cit.aet.artemis.core.dto.OnlineCourseDTO;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseForUserGroupService;
import de.tum.cit.aet.artemis.core.service.course.CourseLoadService;
import de.tum.cit.aet.artemis.core.service.course.CourseOverviewService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;

/**
 * REST controller for managing courses by tutors, editors and instructors.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/")
public class CourseManagementResource {

    private static final Logger log = LoggerFactory.getLogger(CourseManagementResource.class);

    private final CourseLoadService courseLoadService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final SubmissionService submissionService;

    private final AssessmentDashboardService assessmentDashboardService;

    private final CourseForUserGroupService courseForUserGroupService;

    private final CourseOverviewService courseOverviewService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    public CourseManagementResource(UserRepository userRepository, CourseService courseService, CourseRepository courseRepository, AuthorizationCheckService authCheckService,
            TutorParticipationRepository tutorParticipationRepository, SubmissionService submissionService, AssessmentDashboardService assessmentDashboardService,
            ExerciseRepository exerciseRepository, CourseForUserGroupService courseForUserGroupService, CourseOverviewService courseOverviewService,
            CourseLoadService courseLoadService) {
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.submissionService = submissionService;
        this.assessmentDashboardService = assessmentDashboardService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseForUserGroupService = courseForUserGroupService;
        this.courseOverviewService = courseOverviewService;
        this.courseLoadService = courseLoadService;
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
