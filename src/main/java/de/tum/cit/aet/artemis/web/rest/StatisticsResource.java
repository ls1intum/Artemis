package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.enumeration.GraphType;
import de.tum.cit.aet.artemis.domain.enumeration.SpanType;
import de.tum.cit.aet.artemis.domain.enumeration.StatisticsView;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.security.Role;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.StatisticsService;
import de.tum.cit.aet.artemis.web.rest.dto.CourseManagementStatisticsDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExerciseManagementStatisticsDTO;

/**
 * REST controller for managing statistics.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class StatisticsResource {

    private final StatisticsService statisticsService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    public StatisticsResource(StatisticsService statisticsService, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository,
            ExerciseRepository exerciseRepository) {
        this.statisticsService = statisticsService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * GET management/statistics/data-for-content : get the graph data in the last "span" days in the given period for a specific entity, like course,
     * exercise or exam.
     *
     * @param span        the spanTime of which the amount should be calculated
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @param graphType   the type of graph the data should be fetched
     * @param view        the view in which the data will be displayed (Artemis, Course, Exercise)
     * @param entityId    the id of the entity (Course, exercise or exam) for which the data should be fetched
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/data-for-content")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Integer>> getChartData(@RequestParam SpanType span, @RequestParam Integer periodIndex, @RequestParam GraphType graphType,
            @RequestParam StatisticsView view, @RequestParam Long entityId) {
        var courseId = 0L;
        switch (view) {
            case COURSE -> courseId = entityId;
            case EXERCISE -> courseId = exerciseRepository.findByIdElseThrow(entityId).getCourseViaExerciseGroupOrCourseMember().getId();
            case ARTEMIS -> throw new UnsupportedOperationException("Unsupported view: " + view);
        }
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        return ResponseEntity.ok(this.statisticsService.getChartData(span, periodIndex, graphType, view, entityId));
    }

    /**
     * GET management/statistics/course-statistics : get the data for the average score graph in the course statistics
     *
     * @param courseId the id of the course for which the data should be fetched
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/course-statistics")
    @EnforceAtLeastTutor
    public ResponseEntity<CourseManagementStatisticsDTO> getCourseStatistics(@RequestParam Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        return ResponseEntity.ok(this.statisticsService.getCourseStatistics(courseId));
    }

    /**
     * GET management/statistics/exercise-statistics: get the data for the score distribution in the exercise statistics
     *
     * @param exerciseId the id of the exercise for which the data should be fetched
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/exercise-statistics")
    @EnforceAtLeastTutor
    public ResponseEntity<ExerciseManagementStatisticsDTO> getExerciseStatistics(@RequestParam Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        var exerciseManagementStatisticsDTO = statisticsService.getExerciseStatistics(exercise);
        return ResponseEntity.ok(exerciseManagementStatisticsDTO);
    }
}
