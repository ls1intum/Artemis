package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseForCourseDTO(long id, @NotEmpty String title, boolean quizStarted, boolean quizEnded, boolean isEditable, int duration, double maxPoints,
        @Nullable ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, @NotNull IncludedInOverallScore includedInOverallScore,
        @Nullable Set<QuizBatchForCourseDTO> quizBatches) {

    public static QuizExerciseForCourseDTO of(QuizExercise quizExercise, boolean isEditable) {
        Set<QuizBatchForCourseDTO> batches = null;
        if (quizExercise.getQuizBatches() != null) {
            batches = quizExercise.getQuizBatches().stream().map(QuizBatchForCourseDTO::of).collect(Collectors.toSet());
        }
        return new QuizExerciseForCourseDTO(quizExercise.getId(), quizExercise.getTitle(), quizExercise.isQuizStarted(), quizExercise.isQuizEnded(), isEditable,
                quizExercise.getDuration(), quizExercise.getMaxPoints(), quizExercise.getReleaseDate(), quizExercise.getStartDate(), quizExercise.getDueDate(),
                quizExercise.getIncludedInOverallScore(), batches);
    }
}

record QuizBatchForCourseDTO(long id, @Nullable String password, boolean started, boolean ended) {

    public static QuizBatchForCourseDTO of(QuizBatch quizBatch) {
        return new QuizBatchForCourseDTO(quizBatch.getId(), quizBatch.getPassword(), quizBatch.isStarted(), quizBatch.isEnded());
    }
}
