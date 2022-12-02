package de.tum.in.www1.artemis.web.rest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.ExerciseScoresChartService;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresDTO;

/**
 * Controller to provides endpoints to query the necessary data for the exercise-scores-chart.component.ts in the client
 */
@RestController
public class ExerciseScoresChartResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseScoresChartResource.class);

    private final ExerciseScoresChartService exerciseScoresChartService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExerciseDateService exerciseDateService;

    public ExerciseScoresChartResource(ExerciseScoresChartService exerciseScoresChartService, CourseRepository courseRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, ExerciseDateService exerciseDateService) {
        this.exerciseScoresChartService = exerciseScoresChartService;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.exerciseDateService = exerciseDateService;
    }

    /**
     * GET /courses/:courseId/charts/exercise-scores
     * <p>
     * This call returns the information used for the exercise-scores-chart. It will get the score of
     * the requesting user,the average score and the best score in course exercises
     * <p>
     * Note: Only released course exercises with assessment due date over are considered!
     * <p>
     *
     * @param courseId id of the course for which to get the exercise scores
     * @return the ResponseEntity with status 200 (OK) and with the exercise scores in the body
     */
    @GetMapping("courses/{courseId}/charts/exercise-scores")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ExerciseScoresDTO>> getCourseExerciseScores(@PathVariable Long courseId) {
        log.debug("REST request to get exercise scores for course with id: {}", courseId);
        Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        // we only consider exercises in which the student had a chance to earn a score (released and due date over)
        List<ExerciseScoresDTO> exerciseScoresDTOList = exerciseScoresChartService.getExerciseScores(filterExercises(course.getExercises()), user);
        return ResponseEntity.ok(exerciseScoresDTOList);
    }

    private Set<Exercise> filterExercises(Set<Exercise> exercises) {
        return exercises.stream().filter(this::isExerciseFinished).collect(Collectors.toSet());
    }

    private boolean isExerciseFinished(Exercise exercise) {
        // Exercises not visible to the students are not finished either
        if (!exercise.isVisibleToStudents()) {
            return false;
        }

        // If an assessment due date is set, only count the exercise as finished if we passed that date
        if (exercise.getAssessmentDueDate() != null) {
            return ZonedDateTime.now().isAfter(exercise.getAssessmentDueDate());
        }

        // If no assessment due date is set, make sure to only count exercises which have a passed due date
        return exerciseDateService.isAfterLatestDueDate(exercise);
    }
}
