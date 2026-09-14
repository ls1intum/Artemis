package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * REST controller for deleting and importing quiz exercises.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage("authoring/exercise-management")
@RestController
@RequestMapping("api/quiz/")
public class QuizExerciseDeletionResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseDeletionResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizExerciseService quizExerciseService;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final UserRepository userRepository;

    private final Optional<AtlasMLApi> atlasMLApi;

    public QuizExerciseDeletionResource(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, UserRepository userRepository,
            ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService, Optional<AtlasMLApi> atlasMLApi) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userRepository = userRepository;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.atlasMLApi = atlasMLApi;
    }

    /**
     * DELETE /quiz-exercises/:quizExerciseId : delete the "id" quizExercise.
     *
     * @param quizExerciseId the id of the quizExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("quiz-exercises/{quizExerciseId}")
    @EnforceAtLeastInstructorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<Void> deleteQuizExercise(@PathVariable Long quizExerciseId) {
        log.info("REST request to delete quiz exercise : {}", quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndCompetenciesElseThrow(quizExerciseId);
        var user = userRepository.getUserWithAuthorities();

        // Notify AtlasML about the quiz exercise deletion before actual deletion
        notifyAtlasML(quizExercise, OperationTypeDTO.DELETE, "quiz exercise deletion");

        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly and, for quizzes, deletes the drag-and-drop image files
        // (see ExerciseDeletionService#delete) across all deletion entry points.
        exerciseService.logDeletion(quizExercise, quizExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(quizExerciseId, false);
        quizExerciseService.cancelScheduledQuiz(quizExerciseId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, quizExercise.getTitle())).build();
    }

    /**
     * Helper method to notify AtlasML about quiz exercise changes with consistent
     * error handling.
     *
     * @param exercise             the exercise to save
     * @param operationType        the operation type (UPDATE or DELETE)
     * @param operationDescription the description of the operation for logging
     *                                 purposes
     */
    private void notifyAtlasML(QuizExercise exercise, OperationTypeDTO operationType, String operationDescription) {
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(exercise, operationType);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about {}: {}", operationDescription, e.getMessage());
            }
        });
    }

}
