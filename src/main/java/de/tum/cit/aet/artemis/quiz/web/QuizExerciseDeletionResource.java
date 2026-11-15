package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * REST controller for deleting and importing quiz exercises.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.QUIZ_AUTHORING)
@RestController
@RequestMapping("api/quiz/")
public class QuizExerciseDeletionResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseDeletionResource.class);

    private final QuizExerciseService quizExerciseService;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final UserRepository userRepository;

    public QuizExerciseDeletionResource(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, UserRepository userRepository,
            ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userRepository = userRepository;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
    }

    /**
     * DELETE /quiz-exercises/:quizExerciseId : delete the "id" quizExercise in the background. The progress is sent over the websocket topic of the exercise.
     *
     * @param quizExerciseId the id of the quizExercise to delete
     * @return the ResponseEntity with status 202 (Accepted), or 409 (Conflict) if the exercise is already being deleted
     */
    @DeleteMapping("quiz-exercises/{quizExerciseId}")
    @EnforceAtLeastInstructorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<Void> deleteQuizExercise(@PathVariable Long quizExerciseId) {
        log.info("REST request to delete quiz exercise : {}", quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndCompetenciesElseThrow(quizExerciseId);
        var user = userRepository.getUserWithAuthorities();
        var course = quizExercise.getCourseViaExerciseGroupOrCourseMemberElseThrow();

        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly and, for quizzes, deletes the drag-and-drop image files
        // (see ExerciseDeletionService#delete) across all deletion entry points.
        // Audit and cancel the quiz schedule only once the deletion is accepted. A refused duplicate request must do neither.
        // Cancel before the deletion starts so the quiz cannot start while its deletion waits for a free worker.
        exerciseDeletionService.startAsyncDeletion(quizExercise, false, () -> {
            exerciseService.logDeletion(quizExercise, course, user);
            quizExerciseService.cancelScheduledQuiz(quizExerciseId);
        }, () -> quizExerciseService.rescheduleQuizAfterFailedDeletion(quizExerciseId));

        return ResponseEntity.accepted().build();
    }

}
