package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.LearningAnalyticsService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresDTO;

@RestController
@RequestMapping("/api")
public class LearningAnalyticsResource {

    private final Logger log = LoggerFactory.getLogger(LearningAnalyticsResource.class);

    private final LearningAnalyticsService learningAnalyticsService;

    private final CourseRepository courseRepository;

    private final UserService userService;

    private final AuthorizationCheckService authorizationCheckService;

    public LearningAnalyticsResource(LearningAnalyticsService learningAnalyticsService, CourseRepository courseRepository, UserService userService,
            AuthorizationCheckService authorizationCheckService) {
        this.learningAnalyticsService = learningAnalyticsService;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userService = userService;
    }

    /**
     * GET /courses/:courseId/analytics/exercise-scores
     * <p>
     * This call returns the the information used for the exercise-scores-chart. It will get get the score of
     * the requesting user and the average score of the course in course exercises
     * <p>
     * Only released course exercises are considered
     * <p>
     *
     * @return the ResponseEntity with status 200 (OK) and with the exercise scores in the body
     */
    @GetMapping("/courses/{courseId}/analytics/exercise-scores")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ExerciseScoresDTO>> getCourseExerciseScores(@PathVariable Long courseId) {
        log.debug("REST request to get exercise scores for course with id: {}", courseId);
        Optional<Course> courseOptional = Optional.ofNullable(courseRepository.findWithEagerExercisesById(courseId));
        if (courseOptional.isEmpty()) {
            return notFound();
        }
        Course course = courseOptional.get();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
            return forbidden();
        }

        Set<Exercise> exercisesToConsider = course.getExercises().stream().filter(Exercise::isVisibleToStudents).collect(Collectors.toSet());

        List<ExerciseScoresDTO> exerciseScoresDTOList = learningAnalyticsService.getExerciseScores(exercisesToConsider, user);
        return ResponseEntity.ok(exerciseScoresDTOList);
    }

}
