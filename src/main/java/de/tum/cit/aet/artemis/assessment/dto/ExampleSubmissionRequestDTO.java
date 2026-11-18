package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body of the example submission endpoints (create, update, tutor assessment check).
 * <p>
 * The client sends the example submission it loaded, mutated in place, so the record mirrors that shape and reads only
 * what the server acts on: the metadata, the submission content of the exercise type, and (for the tutor assessment
 * check) the feedback of the result the client attached to the submission. Everything else on the wire is ignored.
 * <p>
 * Bare {@code @JsonInclude()}: request bodies must keep {@code false} and empty values on the wire.
 *
 * @param id                    the id of the example submission, null when creating a new one
 * @param usedForTutorial       whether tutors have to assess the example correctly (null keeps the entity default)
 * @param assessmentExplanation the instructor's explanation of the example assessment
 * @param exercise              the exercise reference the client echoes; when present, its id must match the path
 * @param submission            the submitted content (text or model), null is rejected by the server
 */
@JsonInclude()
public record ExampleSubmissionRequestDTO(@Nullable Long id, @Nullable Boolean usedForTutorial, @Nullable String assessmentExplanation, @Nullable ExerciseReferenceDTO exercise,
        @Nullable SubmissionRequestDTO submission) {

    /**
     * The exercise the client attached to the example submission. Only the id is read.
     *
     * @param id the exercise id
     */
    @JsonInclude()
    public record ExerciseReferenceDTO(@Nullable Long id) {
    }

    /**
     * The content of the example submission. Text exercises use {@code text}, modeling exercises use {@code model} and
     * {@code explanationText}; the other fields are ignored for that exercise type.
     *
     * @param id              the id of the submission (ignored, the server resolves it through the example submission)
     * @param text            the text of a text submission
     * @param model           the JSON model of a modeling submission
     * @param explanationText the explanation of a modeling submission
     * @param results         the results the client attached; only read by the tutor assessment check
     */
    @JsonInclude()
    public record SubmissionRequestDTO(@Nullable Long id, @Nullable String text, @Nullable String model, @Nullable String explanationText,
            @Nullable List<ResultRequestDTO> results) {
    }

    /**
     * A result the client attached to the submission. Only the feedback is read (tutor assessment check).
     *
     * @param feedbacks the feedback of the result
     */
    @JsonInclude()
    public record ResultRequestDTO(@Nullable List<FeedbackDTO> feedbacks) {
    }
}
