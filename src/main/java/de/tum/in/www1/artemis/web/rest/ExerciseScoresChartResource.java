package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseScoresChartService;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

@RestController
@RequestMapping("/api")
public class ExerciseScoresChartResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseScoresChartResource.class);

    private final ExerciseScoresChartService exerciseScoresChartService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public ExerciseScoresChartResource(ExerciseScoresChartService exerciseScoresChartService, CourseRepository courseRepository, UserRepository userRepository,
                                       AuthorizationCheckService authorizationCheckService) {
        this.exerciseScoresChartService = exerciseScoresChartService;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
    }

    /**
     * GET /courses/:courseId/charts/exercise-scores
     * <p>
     * This call returns the the information used for the exercise-scores-chart. It will get get the score of
     * the requesting user,the average score and the best score in course exercises
     * <p>
     * Only released course exercises with assessment due date over are considered!
     * <p>
     *
     * @return the ResponseEntity with status 200 (OK) and with the exercise scores in the body
     */
    @GetMapping("/courses/{courseId}/charts/exercise-scores")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ExerciseScoresDTO>> getCourseExerciseScores(@PathVariable Long courseId) {
        log.debug("REST request to get exercise scores for course with id: {}", courseId);
        Optional<Course> courseOptional = Optional.ofNullable(courseRepository.findWithEagerExercisesById(courseId));
        if (courseOptional.isEmpty()) {
            return notFound();
        }
        Course course = courseOptional.get();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
            return forbidden();
        }
        // we only consider exercises in which the student had a chance to earn a score (released and due date over)
        Set<Exercise> exercisesToConsider = course.getExercises().stream().filter(Exercise::isVisibleToStudents).filter(Exercise::isAssessmentDueDateOver)
            .collect(Collectors.toSet());
        List<ExerciseScoresDTO> exerciseScoresDTOList = exerciseScoresChartService.getExerciseScores(exercisesToConsider, user);
        return ResponseEntity.ok(exerciseScoresDTOList);
    }

}
