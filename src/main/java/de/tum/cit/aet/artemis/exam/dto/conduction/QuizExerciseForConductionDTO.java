package de.tum.cit.aet.artemis.exam.dto.conduction;

import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionForExamDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;

/**
 * Quiz-exercise-specific fields carried in the conduction / summary payload (unwrapped into the exercise object).
 * <p>
 * On the conduction path (and on the summary path before results are published) the quiz questions are projected to
 * their solution-hidden shape ({@link QuizQuestionWithoutSolutionDTO}), matching the masked entity wire: answerable ids
 * are kept, correct answers / mappings / solutions are not. Once the student exam's results are published, the summary
 * factory passes {@code includeSolutions = true} so the questions are projected with their full solutions
 * ({@link QuizQuestionWithSolutionDTO}: {@code isCorrect}, {@code explanation}, correct drag-and-drop / short-answer
 * mappings), which the post-publish quiz summary UI renders. The publish decision is made by the caller
 * ({@code StudentExamForSummaryDTO.of} via {@code StudentExam#areResultsPublishedYet}); this factory never re-adds a
 * solution the caller did not ask for.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseForConductionDTO(Integer allowedNumberOfAttempts, Integer duration, boolean quizStarted, boolean quizEnded, QuizMode quizMode,
        Boolean randomizeQuestionOrder, @JsonDeserialize(contentAs = QuizQuestionWithSolutionDTO.class) List<QuizQuestionForExamDTO> quizQuestions) {

    /**
     * Extracts the quiz-specific fields, guarding against uninitialized lazy quiz-question collections.
     *
     * @param quizExercise     the quiz exercise to convert
     * @param includeSolutions whether the quiz questions should carry their full solutions ({@code true} only once the
     *                             student exam's results are published, decided by the summary caller)
     * @return the quiz-specific fields
     */
    public static QuizExerciseForConductionDTO of(QuizExercise quizExercise, boolean includeSolutions) {
        var entityQuestions = quizExercise.getQuizQuestions();
        List<QuizQuestionForExamDTO> quizQuestions = (entityQuestions == null || !Hibernate.isInitialized(entityQuestions)) ? null
                : entityQuestions.stream()
                        .<QuizQuestionForExamDTO>map(question -> includeSolutions ? QuizQuestionWithSolutionDTO.of(question) : QuizQuestionWithoutSolutionDTO.of(question))
                        .toList();
        return new QuizExerciseForConductionDTO(quizExercise.getAllowedNumberOfAttempts(), quizExercise.getDuration(), quizExercise.isQuizStarted(), quizExercise.isQuizEnded(),
                quizExercise.getQuizMode(), quizExercise.isRandomizeQuestionOrder(), quizQuestions);
    }
}
