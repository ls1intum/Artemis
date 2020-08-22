package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.StudentScoresService;
import de.tum.in.www1.artemis.service.UserService;

/**
 * REST controller for managing Rating.
 */
@Validated
@RestController
@RequestMapping("/api")
public class StudentScoresResource {

    // private static final String ENTITY_NAME = "studentScores";

    private final Logger log = LoggerFactory.getLogger(StudentScoresResource.class);

    private final StudentScoresService studentScoresService;

    private final UserService userService;

    private final ExerciseService exerciseService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    public StudentScoresResource(StudentScoresService studentScoresService, UserService userService, ExerciseService exerciseService, CourseService courseService,
            AuthorizationCheckService authCheckService) {
        this.studentScoresService = studentScoresService;
        this.userService = userService;
        this.exerciseService = exerciseService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /student-scores/exercise/{exerciseId} : Find StudentScores by exercise id.
     *
     * @param exerciseId id of the exercise
     * @return the ResponseEntity with status 200 (OK) and with the found student scores as body
     */
    @GetMapping("/student-scores/exercise/{exerciseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<StudentScore>> getStudentScoresForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get student scores for exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            return forbidden();
        }

        List<StudentScore> studentScores = studentScoresService.getStudentScoresForExercise(exerciseId);

        return ResponseEntity.ok(studentScores);
    }

    /**
     * GET /student-scores/course/{courseId} : Find StudentScores by course id.
     *
     * @param courseId id of the course
     * @return the ResponseEntity with status 200 (OK) and with the found student scores as body
     */
    @GetMapping("/student-scores/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<StudentScore>> getStudentScoresForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get student scores for exercise : {}", courseId);
        Course course = courseService.findOneWithExercises(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        List<StudentScore> studentScores = studentScoresService.getStudentScoresForCourse(course);

        return ResponseEntity.ok(studentScores);
    }
}
