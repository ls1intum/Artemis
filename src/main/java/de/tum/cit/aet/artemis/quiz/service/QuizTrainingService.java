package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.QuizTrainingAnswerDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerAfterEvaluationDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizTrainingService {

    private final QuizQuestionProgressService quizQuestionProgressService;

    private final QuizQuestionRepository quizQuestionRepository;

    public QuizTrainingService(QuizQuestionProgressService quizQuestionProgressService, QuizQuestionRepository quizQuestionRepository) {
        this.quizQuestionProgressService = quizQuestionProgressService;
        this.quizQuestionRepository = quizQuestionRepository;
    }

    /**
     * Submits a quiz question for training mode, calculates scores and creates a result.
     *
     * @param quizQuestionId         the id of the quiz question being submitted
     * @param userId                 the id of the user who is submitting the quiz
     * @param studentSubmittedAnswer the answer submitted by the user
     * @param answeredAt             the time when the question was answered
     * @return a DTO containing the submitted answer after the evaluation
     */
    public SubmittedAnswerAfterEvaluationDTO submitForTraining(long quizQuestionId, long userId, QuizTrainingAnswerDTO studentSubmittedAnswer, ZonedDateTime answeredAt,
            long courseId) {
        QuizQuestion quizQuestion = quizQuestionRepository.findByIdElseThrow(quizQuestionId);
        SubmittedAnswer answer = studentSubmittedAnswer.submittedAnswer();

        double score = quizQuestion.scoreForAnswer(answer);

        answer.setScoreInPoints(score);
        answer.setQuizQuestion(quizQuestion);

        quizQuestionProgressService.saveProgressFromTraining(quizQuestion, userId, answer, answeredAt, courseId);

        return SubmittedAnswerAfterEvaluationDTO.of(answer);
    }
}
