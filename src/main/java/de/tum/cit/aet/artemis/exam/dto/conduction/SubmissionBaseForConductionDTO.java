package de.tum.cit.aet.artemis.exam.dto.conduction;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * Common fields shared by every submission subtype in the conduction / summary payload. The
 * {@code submissionExerciseType} discriminator mirrors the {@code @JsonTypeInfo} property on {@link Submission}, so the
 * (unchanged) client model and the byte-compat oracle tests deserialize each submission into the correct concrete
 * subtype.
 * <p>
 * The {@code results} are only populated once the exam is submitted and (for the student view) the results are
 * published; the exam masking pipeline strips them otherwise, so this factory faithfully copies whatever is present on
 * the already-masked entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionBaseForConductionDTO(String submissionExerciseType, long id, Boolean submitted, Boolean empty, ZonedDateTime submissionDate,
        List<ResultForConductionDTO> results) {

    /**
     * Extracts the common submission fields. {@code empty} is only carried for the non-quiz submission types, matching
     * the entity wire (a quiz submission does not serialize an {@code empty} flag).
     *
     * @param submission the submission to convert
     * @return the common submission fields
     */
    public static SubmissionBaseForConductionDTO of(Submission submission) {
        String submissionExerciseType = submission.getSubmissionExerciseType();
        Boolean empty = "quiz".equals(submissionExerciseType) ? null : submission.isEmpty();
        var entityResults = submission.getResults();
        List<ResultForConductionDTO> results = (entityResults == null || !Hibernate.isInitialized(entityResults) || entityResults.isEmpty()) ? null
                : entityResults.stream().map(ResultForConductionDTO::of).toList();
        return new SubmissionBaseForConductionDTO(submissionExerciseType, submission.getId(), submission.isSubmitted(), empty, submission.getSubmissionDate(), results);
    }
}
