package de.tum.cit.aet.artemis.quiz.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;

@Profile(PROFILE_CORE)
@Controller
@Lazy
public class QuizQuestionApi implements AbstractApi {

    private final QuizQuestionRepository quizQuestionRepository;

    private final QuizSubmissionService quizSubmissionService;

    public QuizQuestionApi(QuizQuestionRepository quizQuestionRepository, QuizSubmissionService quizSubmissionService) {
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizSubmissionService = quizSubmissionService;
    }

    public long countAllQuizQuestionsByCourseIdAvailableForPractice(long courseId) {
        ZonedDateTime now = ZonedDateTime.now();
        return quizQuestionRepository.countAllQuizQuestionsByCourseIdBefore(courseId, now);
    }

    public boolean areQuizExercisesInCourseAvailableForPractice(long courseId) {
        ZonedDateTime now = ZonedDateTime.now();
        return quizQuestionRepository.areQuizExercisesWithDueDateBefore(courseId, now);
    }

    public Slice<QuizQuestion> findAllQuizQuestionsByCourseIdAvailableForPractice(long courseId, Pageable pageable) {
        ZonedDateTime now = ZonedDateTime.now();
        return quizQuestionRepository.findAllQuizQuestionsByCourseIdWithDueDateBefore(courseId, now, pageable);
    }

    public Slice<QuizQuestion> findAllQuizQuestionsByCourseIdAvailableForPracticeNotIn(Set<Long> ids, long courseId, Pageable pageable) {
        ZonedDateTime now = ZonedDateTime.now();
        return quizQuestionRepository.findAllQuizQuestionsByCourseIdWithDueDateBeforeNotIn(ids, courseId, now, pageable);
    }

    /**
     * Finds all quiz questions in a course that are available for practice (due date has passed).
     *
     * @param courseId the ID of the course
     * @return list of available quiz questions
     */
    public List<QuizQuestion> findAllPracticeQuestionsForCourse(long courseId) {
        return quizQuestionRepository.findAllQuizQuestionsByCourseIdWithDueDateBefore(courseId, ZonedDateTime.now(), Pageable.unpaged()).getContent();
    }

    /**
     * Finds a quiz question by id and course id if available for practice (due date has passed).
     *
     * @param questionId the ID of the question
     * @param courseId   the ID of the course
     * @return optional containing the quiz question if found
     */
    public Optional<QuizQuestion> findByIdAndCourseId(long questionId, long courseId) {
        return quizQuestionRepository.findByIdAndCourseIdWithDueDateBefore(questionId, courseId, ZonedDateTime.now());
    }

    /**
     * Evaluates a submitted answer against the question without persisting results in the grading tables.
     *
     * @param question  the quiz question
     * @param answerDto the student's submitted answer DTO
     * @return the score achieved for this answer in points
     */
    public double evaluateSubmittedAnswer(QuizQuestion question, SubmittedAnswerFromLiveClientDTO answerDto) {
        if (answerDto == null) {
            return 0.0;
        }
        SubmittedAnswer submittedAnswer = quizSubmissionService.convertSubmittedAnswerForTraining(answerDto, question);
        return question.scoreForAnswer(submittedAnswer);
    }
}
