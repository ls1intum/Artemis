package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.List;
import java.util.Optional;

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
import de.tum.in.www1.artemis.domain.scores.TutorScore;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.TutorScoreService;
import de.tum.in.www1.artemis.service.UserService;

/**
 * REST controller for managing Rating.
 */
@Validated
@RestController
@RequestMapping("/api")
public class TutorScoreResource {

    // private static final String ENTITY_NAME = "tutorScores";

    private final Logger log = LoggerFactory.getLogger(TutorScoreResource.class);

    private final TutorScoreService tutorScoreService;

    private final UserService userService;

    private final ExerciseService exerciseService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    public TutorScoreResource(TutorScoreService tutorScoreService, UserService userService, ExerciseService exerciseService, CourseService courseService,
            AuthorizationCheckService authCheckService) {
        this.tutorScoreService = tutorScoreService;
        this.userService = userService;
        this.exerciseService = exerciseService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /tutor-scores/exercise/{exerciseId} : Find TutorScores by exercise id.
     *
     * @param exerciseId    id of the exercise
     * @return the ResponseEntity with status 200 (OK) and with the found tutor scores as body
     */
    @GetMapping("/tutor-scores/exercise/{exerciseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<List<TutorScore>> getTutorScoresForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get student scores for exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }

        List<TutorScore> tutorScores = tutorScoreService.getTutorScoresForExercise(exercise);

        return ResponseEntity.ok(tutorScores);
    }

    /**
     * GET /tutor-scores/course/{courseId} : Find TutorScores by course id.
     *
     * @param courseId    id of the course
     * @return the ResponseEntity with status 200 (OK) and with the found tutor scores as body
     */
    @GetMapping("/tutor-scores/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<List<TutorScore>> getTutorScoresForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get student scores for exercise : {}", courseId);
        Course course = courseService.findOneWithExercises(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }

        List<TutorScore> tutorScores = tutorScoreService.getTutorScoresForCourse(course);

        return ResponseEntity.ok(tutorScores);
    }

    /**
     * GET /tutor-scores/exercise/{exerciseId}/tutor/{tutorLogin} : Find TutorScores by exercise id and tutor login.
     *
     * @param exerciseId id of the exercise
     * @param tutorLogin login of the tutor
     * @return the ResponseEntity with status 200 (OK) and with the found tutor score as body
     */
    @GetMapping("/tutor-scores/exercise/{exerciseId}/tutor/{tutorLogin}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<Optional<TutorScore>> getTutorScoreForExerciseAndTutor(@PathVariable Long exerciseId, @PathVariable String tutorLogin) {
        log.debug("REST request to get tutor score for tutor {} and exercise {}", tutorLogin, exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        Optional<User> tutor = userService.getUserByLogin(tutorLogin);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }

        Optional<TutorScore> tutorScore = tutorScoreService.getTutorScoreForTutorAndExercise(tutor.get(), exercise);
        return ResponseEntity.ok(tutorScore);
    }
}
