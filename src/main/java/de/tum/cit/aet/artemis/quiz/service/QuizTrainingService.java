package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.QuizTrainingAnswerDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerAfterEvaluationDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizTrainingService {

    private static final Logger log = LoggerFactory.getLogger(QuizTrainingService.class);

    private final QuizQuestionProgressService quizQuestionProgressService;

    private final QuizQuestionRepository quizQuestionRepository;

    public QuizTrainingService(QuizQuestionProgressService quizQuestionProgressService, QuizQuestionRepository quizQuestionRepository) {
        this.quizQuestionProgressService = quizQuestionProgressService;
        this.quizQuestionRepository = quizQuestionRepository;
    }

    /**
     * Submits a quiz question for training mode, calculates scores and creates a result.
     *
     * @param courseId        the id of the course to which the quiz belongs
     * @param quizQuestionId  the id of the quiz question being submitted
     * @param user            the user who is submitting the quiz
     * @param submittedAnswer the answer submitted by the user
     */
    public SubmittedAnswerAfterEvaluationDTO submitForTraining(Long courseId, Long quizQuestionId, User user, QuizTrainingAnswerDTO submittedAnswer) {
        log.info("Received DTO: {}", submittedAnswer);
        // Get the quiz question
        QuizQuestion quizQuestion = quizQuestionRepository.findByIdElseThrow(quizQuestionId);
        log.info("Found quiz question: {}", quizQuestion.getId());

        SubmittedAnswer answer = submittedAnswer.getSubmittedAnswer();
        if (answer == null) {
            log.error("No submitted answer found in DTO");
            throw new IllegalArgumentException("No submitted answer provided");
        }
        log.info("Processing answer of type: {}", answer.getClass().getSimpleName());

        double score = quizQuestion.scoreForAnswer(answer);
        log.info("Calculated score: {}", score);

        answer.setScoreInPoints(score);
        answer.setQuizQuestion(quizQuestion);

        SubmittedAnswerAfterEvaluationDTO result = SubmittedAnswerAfterEvaluationDTO.of(answer);
        log.info("Created result DTO: {}", result);

        return result;
    }
}
