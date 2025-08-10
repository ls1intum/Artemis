package de.tum.cit.aet.artemis.core.web.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseManagementDetailViewDTO;
import de.tum.cit.aet.artemis.core.dto.CourseManagementOverviewStatisticsDTO;
import de.tum.cit.aet.artemis.core.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseForUserGroupService;
import de.tum.cit.aet.artemis.core.service.course.CourseOverviewService;
import de.tum.cit.aet.artemis.core.service.course.CourseStatsService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;

/**
 * REST controller for managing Course.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/")
@Lazy
public class CourseStatsResource {

    private static final Logger log = LoggerFactory.getLogger(CourseStatsResource.class);

    private final UserRepository userRepository;

    private final CourseStatsService courseStatsService;

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final ExerciseService exerciseService;

    private final CourseForUserGroupService courseForUserGroupService;

    private final ExerciseRepository exerciseRepository;

    private final GradingScaleRepository gradingScaleRepository;

    private final CourseOverviewService courseOverviewService;

    public CourseStatsResource(UserRepository userRepository, CourseStatsService courseStatsService, CourseRepository courseRepository, ExerciseService exerciseService,
            AuthorizationCheckService authCheckService, CourseForUserGroupService courseForUserGroupService, ExerciseRepository exerciseRepository,
            GradingScaleRepository gradingScaleRepository, CourseOverviewService courseOverviewService) {
        this.courseStatsService = courseStatsService;
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.courseForUserGroupService = courseForUserGroupService;
        this.exerciseRepository = exerciseRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.courseOverviewService = courseOverviewService;
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

        User user = userRepository.getUserWithGroupsAndAuthorities();
        List<Course> courses = courseForUserGroupService.getCoursesForTutors(user, onlyActive);
        for (Course course : courses) {
            course.setNumberOfInstructors(userRepository.countUserInGroup(course.getInstructorGroupName()));
            course.setNumberOfTeachingAssistants(userRepository.countUserInGroup(course.getTeachingAssistantGroupName()));
            course.setNumberOfEditors(userRepository.countUserInGroup(course.getEditorGroupName()));
            course.setNumberOfStudents(userRepository.countUserInGroup(course.getStudentGroupName()));
        }
        return ResponseEntity.ok(courses);
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
        StatsForDashboardDTO stats = courseStatsService.getStatsForDashboardDTO(course);
        return ResponseEntity.ok(stats);
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
        for (final var course : courseOverviewService.getAllCoursesForManagementOverview(onlyActive)) {
            final var courseId = course.getId();
            var studentsGroup = course.getStudentGroupName();
            var amountOfStudentsInCourse = Math.toIntExact(userRepository.countUserInGroup(studentsGroup));
            var exerciseStatistics = exerciseService.getStatisticsForCourseManagementOverview(courseId, amountOfStudentsInCourse);

            var exerciseIds = exerciseRepository.findExerciseIdsByCourseId(courseId);
            var endDate = courseStatsService.determineEndDateForActiveStudents(course);
            var timeSpanSize = courseStatsService.determineTimeSpanSizeForActiveStudents(course, endDate, 4);
            var activeStudents = courseStatsService.getActiveStudents(exerciseIds, 0, timeSpanSize, endDate);

            final var courseDTO = new CourseManagementOverviewStatisticsDTO(courseId, activeStudents, exerciseStatistics);
            courseDTOs.add(courseDTO);
        }

        return ResponseEntity.ok(courseDTOs);
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
        var exerciseIds = exerciseRepository.findExerciseIdsByCourseId(courseId);
        var chartEndDate = courseStatsService.determineEndDateForActiveStudents(course);
        var spanEndDate = chartEndDate.plusWeeks(periodSize.orElse(17) * periodIndex);
        var returnedSpanSize = courseStatsService.determineTimeSpanSizeForActiveStudents(course, spanEndDate, periodSize.orElse(17));
        var activeStudents = courseStatsService.getActiveStudents(exerciseIds, periodIndex, Math.min(returnedSpanSize, periodSize.orElse(17)), chartEndDate);
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
        var course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        var exerciseIds = exerciseRepository.findExerciseIdsByCourseId(courseId);
        if (course.getStartDate() == null) {
            throw new IllegalArgumentException("Course does not contain start date");
        }
        var endDate = courseStatsService.determineEndDateForActiveStudents(course);
        var returnedSpanSize = courseStatsService.calculateWeeksBetweenDates(course.getStartDate(), endDate);
        var activeStudents = courseStatsService.getActiveStudents(exerciseIds, 0, Math.toIntExact(returnedSpanSize), endDate);
        return ResponseEntity.ok(activeStudents);
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
        GradingScale gradingScale = gradingScaleRepository.findByCourseId(courseId).orElse(null);
        CourseManagementDetailViewDTO managementDetailViewDTO = courseStatsService.getStatsForDetailView(course, gradingScale);
        return ResponseEntity.ok(managementDetailViewDTO);
    }
}
