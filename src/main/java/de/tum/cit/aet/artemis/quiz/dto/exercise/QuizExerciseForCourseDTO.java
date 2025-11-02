package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.Objects;
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

    /**
     * Converts a QuizExercise to a QuizExerciseForCourseDTO
     *
     * @param quizExercise The quiz exercise to convert
     * @param isEditable   Whether the quiz exercise is editable
     * @return The converted QuizExerciseForCourseDTO
     */
    public static QuizExerciseForCourseDTO of(QuizExercise quizExercise, boolean isEditable) {
        Set<QuizBatchForCourseDTO> batches = null;
        if (quizExercise.getQuizBatches() != null) {
            batches = quizExercise.getQuizBatches().stream().map(QuizBatchForCourseDTO::of).collect(Collectors.toSet());
        }
        return new QuizExerciseForCourseDTO(quizExercise.getId(), quizExercise.getTitle(), quizExercise.isQuizStarted(), quizExercise.isQuizEnded(), isEditable,
                quizExercise.getDuration(), quizExercise.getMaxPoints(), quizExercise.getReleaseDate(), quizExercise.getStartDate(), quizExercise.getDueDate(),
                quizExercise.getIncludedInOverallScore(), batches);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        QuizExerciseForCourseDTO that = (QuizExerciseForCourseDTO) o;
        if (id != that.id) {
            return false;
        }
        if (quizStarted != that.quizStarted) {
            return false;
        }
        if (quizEnded != that.quizEnded) {
            return false;
        }
        if (isEditable != that.isEditable) {
            return false;
        }
        if (duration != that.duration) {
            return false;
        }
        if (Double.compare(that.maxPoints, maxPoints) != 0) {
            return false;
        }
        if (!title.equals(that.title)) {
            return false;
        }
        if (!Objects.equals(releaseDate, that.releaseDate)) {
            return false;
        }
        if (!Objects.equals(startDate, that.startDate)) {
            return false;
        }
        if (!Objects.equals(dueDate, that.dueDate)) {
            return false;
        }
        if (!includedInOverallScore.equals(that.includedInOverallScore)) {
            return false;
        }
        boolean thisBatchesEmpty = quizBatches == null || quizBatches.isEmpty();
        boolean thatBatchesEmpty = that.quizBatches == null || that.quizBatches.isEmpty();
        if (thisBatchesEmpty && thatBatchesEmpty) {
            return true;
        }
        if (thisBatchesEmpty != thatBatchesEmpty) {
            return false;
        }
        return quizBatches.equals(that.quizBatches);
    }

}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizBatchForCourseDTO(long id, @Nullable String password, boolean started, boolean ended) {

    public static QuizBatchForCourseDTO of(QuizBatch quizBatch) {
        return new QuizBatchForCourseDTO(quizBatch.getId(), quizBatch.getPassword(), quizBatch.isStarted(), quizBatch.isEnded());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        QuizBatchForCourseDTO that = (QuizBatchForCourseDTO) o;
        if (id != that.id)
            return false;
        if (started != that.started)
            return false;
        if (ended != that.ended)
            return false;
        return Objects.equals(password, that.password);
    }
}
