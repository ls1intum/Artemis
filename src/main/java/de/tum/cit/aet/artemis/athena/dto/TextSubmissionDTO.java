package de.tum.cit.aet.artemis.athena.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * A DTO representing a TextSubmission, for transferring data to Athena
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextSubmissionDTO(long id, long exerciseId, String text, String language) implements SubmissionBaseDTO {

    /**
     * Creates a new TextSubmissionDTO from a TextSubmission. The DTO also contains the exerciseId of the exercise the submission belongs to.
     *
     * @param exerciseId The id of the exercise the submission belongs to
     * @param submission The submission to create the DTO from
     * @return The created DTO
     */
    public static TextSubmissionDTO of(long exerciseId, @NotNull TextSubmission submission) {
        String language = null;
        if (submission.getLanguage() != null) {
            language = submission.getLanguage().toString();
        }
        return new TextSubmissionDTO(submission.getId(), exerciseId, submission.getText(), language);
    }
}
