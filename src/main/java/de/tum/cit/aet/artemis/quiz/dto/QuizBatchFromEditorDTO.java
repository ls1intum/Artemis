package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;

/**
 * DTO for quiz batches in the editor context.
 * Supports both creating new batches (id is null) and updating existing batches (id is non-null).
 *
 * @param id        the ID of the batch, null for new batches
 * @param startTime the start time of the batch
 * @param password  the password for the batch (for batched mode)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizBatchFromEditorDTO(Long id, ZonedDateTime startTime, String password) {

    /**
     * Creates a QuizBatchFromEditorDTO from the given QuizBatch domain object.
     *
     * @param quizBatch the quiz batch to convert
     * @return the corresponding DTO
     */
    public static QuizBatchFromEditorDTO of(QuizBatch quizBatch) {
        return new QuizBatchFromEditorDTO(quizBatch.getId(), quizBatch.getStartTime(), quizBatch.getPassword());
    }

    /**
     * Creates a new QuizBatch domain object from this DTO.
     * Note: This should only be used for new batches (id is null).
     * For existing batches, use applyTo() to update the existing entity.
     *
     * @return a new QuizBatch domain object
     */
    public QuizBatch toDomainObject() {
        QuizBatch quizBatch = new QuizBatch();
        quizBatch.setStartTime(this.startTime);
        quizBatch.setPassword(this.password);
        return quizBatch;
    }

    /**
     * Applies the DTO values to an existing QuizBatch entity.
     *
     * @param quizBatch the existing quiz batch to update
     */
    public void applyTo(QuizBatch quizBatch) {
        quizBatch.setStartTime(this.startTime);
        quizBatch.setPassword(this.password);
    }
}
