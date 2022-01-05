package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.domain.enumeration.StatisticsView;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseManagementStatisticsDTO;

/**
 * REST controller for managing statistics.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('TA')")
public class StatisticsResource {

    private final Logger log = LoggerFactory.getLogger(StatisticsResource.class);

    private final StatisticsService service;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    public StatisticsResource(StatisticsService service, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository,
            ExerciseRepository exerciseRepository) {
        this.service = service;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * GET management/statistics/data : get the graph data in the last "span" days in the given period.
     *
     * @param span        the spanTime of which the amount should be calculated
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @param graphType   the type of graph the data should be fetched
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Integer>> getChartData(@RequestParam SpanType span, @RequestParam Integer periodIndex, @RequestParam GraphType graphType) {
        log.debug("REST request to get graph data");
        return ResponseEntity.ok(this.service.getChartData(span, periodIndex, graphType, StatisticsView.ARTEMIS, null));
    }

    /**
     * GET management/statistics/data-for-content : get the graph data in the last "span" days in the given period for a specific entity, like course,
     *                                              exercise or exam.
     *
     * @param span        the spanTime of which the amount should be calculated
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @param graphType   the type of graph the data should be fetched
     * @param view        the view in which the data will be displayed (Artemis, Course, Exercise)
     * @param entityId    the id of the entity (Course, exercise or exam) for which the data should be fetched
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/data-for-content")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Integer>> getChartData(@RequestParam SpanType span, @RequestParam Integer periodIndex, @RequestParam GraphType graphType,
            @RequestParam StatisticsView view, @RequestParam Long entityId) {
        var courseId = 0L;
        switch (view) {
            case COURSE -> courseId = entityId;
            case EXERCISE -> courseId = exerciseRepository.findByIdElseThrow(entityId).getCourseViaExerciseGroupOrCourseMember().getId();
            case ARTEMIS -> throw new UnsupportedOperationException("Unsupported view: " + view);
        }
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
            return forbidden();
        }
        return ResponseEntity.ok(this.service.getChartData(span, periodIndex, graphType, view, entityId));
    }

    /**
     * GET management/statistics/course-statistics : get the data for the average score graph in the course statistics
     *
     * @param courseId    the id of the course for which the data should be fetched
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/course-statistics")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<CourseManagementStatisticsDTO> getCourseStatistics(@RequestParam Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        return ResponseEntity.ok(this.service.getCourseStatistics(courseId));
    }

    /**
     * GET management/statistics/exercise-statistics: get the data for the score distribution in the exercise statistics
     *
     * @param exerciseId    the id of the exercise for which the data should be fetched
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/exercise-statistics")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ExerciseManagementStatisticsDTO> getExerciseStatistics(@RequestParam Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (!authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise, null)) {
            return forbidden();
        }
        var exerciseManagementStatisticsDTO = service.getExerciseStatistics(exercise);
        return ResponseEntity.ok(exerciseManagementStatisticsDTO);
    }
}
