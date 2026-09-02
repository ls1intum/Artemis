package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.quiz.config.QuizLegacyRestPaths;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.QuizPointStatisticsDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizQuestionStatisticResponseDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizStatisticsOverviewDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizStatisticsService;

/**
 * REST controller for calculating quiz statistics on demand.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizStatisticsResource {

    private static final Logger log = LoggerFactory.getLogger(QuizStatisticsResource.class);

    private final QuizStatisticsService quizStatisticsService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    public QuizStatisticsResource(QuizStatisticsService quizStatisticsService, QuizExerciseRepository quizExerciseRepository, AuthorizationCheckService authCheckService,
            UserRepository userRepository) {
        this.quizStatisticsService = quizStatisticsService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    /**
     * Gets the counters needed by the quiz statistics overview.
     *
     * @param quizExerciseId the id of the quiz exercise
     * @return the quiz exercise with overview statistics
     */
    @GetMapping("quiz-exercises/{quizExerciseId}/statistics/overview")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizStatisticsOverviewDTO> getQuizStatisticsOverview(@PathVariable long quizExerciseId) {
        QuizExercise quizExercise = getQuizExerciseForStatistics(quizExerciseId);
        log.debug("REST request to calculate quiz statistics overview: {}", quizExerciseId);
        return ResponseEntity.ok(quizStatisticsService.getOverview(quizExercise));
    }

    /**
     * Gets the point-bucket histogram of a quiz.
     * The legacy recalculation path maps here because on-demand statistics have no persisted aggregate to recalculate.
     *
     * @param quizExerciseId the id of the quiz exercise
     * @return the quiz exercise with point statistics
     */
    @SuppressWarnings("deprecation")
    @GetMapping({ "quiz-exercises/{quizExerciseId}/statistics/points", QuizLegacyRestPaths.RECALCULATE_STATISTICS })
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizPointStatisticsDTO> getQuizPointStatistic(@PathVariable long quizExerciseId) {
        QuizExercise quizExercise = getQuizExerciseForStatistics(quizExerciseId);
        log.debug("REST request to calculate quiz point statistic: {}", quizExerciseId);
        return ResponseEntity.ok(quizStatisticsService.getPointStatistic(quizExercise));
    }

    /**
     * Gets all counters for one question of a quiz.
     *
     * @param quizExerciseId the id of the quiz exercise
     * @param questionId     the id of the question
     * @return the quiz exercise with the requested question statistic
     */
    @GetMapping("quiz-exercises/{quizExerciseId}/statistics/questions/{questionId}")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizQuestionStatisticResponseDTO> getQuizQuestionStatistic(@PathVariable long quizExerciseId, @PathVariable long questionId) {
        QuizExercise quizExercise = getQuizExerciseForStatistics(quizExerciseId);
        log.debug("REST request to calculate statistic for question {} of quiz {}", questionId, quizExerciseId);
        return ResponseEntity.ok(quizStatisticsService.getQuestionStatistic(quizExercise, questionId));
    }

    private QuizExercise getQuizExerciseForStatistics(long quizExerciseId) {
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndCategoriesAndBatchesElseThrow(quizExerciseId);
        if (quizExercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, quizExercise, userRepository.getUserWithAuthorities());
        }
        return quizExercise;
    }
}
