package de.tum.cit.aet.artemis.course.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.core.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.course.config.CourseLegacyRestPaths;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseManagementDetailViewDTO;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseStatsService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * REST controller for managing Course.
 */
@Profile(PROFILE_CORE)
@FeatureUsage("analytics/course-statistics")
@RestController
@SuppressWarnings("deprecation")
@RequestMapping({ "api/course/", CourseLegacyRestPaths.CORE_PREFIX })
@Lazy
public class CourseStatsResource {

    private static final Logger log = LoggerFactory.getLogger(CourseStatsResource.class);

    private final UserRepository userRepository;

    private final CourseStatsService courseStatsService;

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    private final GradingScaleRepository gradingScaleRepository;

    public CourseStatsResource(UserRepository userRepository, CourseStatsService courseStatsService, CourseRepository courseRepository, AuthorizationCheckService authCheckService,
            ExerciseRepository exerciseRepository, GradingScaleRepository gradingScaleRepository) {
        this.courseStatsService = courseStatsService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.gradingScaleRepository = gradingScaleRepository;
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
        var startTime = System.currentTimeMillis();
        CourseManagementDetailViewDTO managementDetailViewDTO = courseStatsService.getStatsForDetailView(course, gradingScale);
        var endTime = System.currentTimeMillis();
        log.debug("Getting data for course management detail view took {} ms", (endTime - startTime));
        return ResponseEntity.ok(managementDetailViewDTO);
    }
}
