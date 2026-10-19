package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Read DTO for an {@link ExampleSubmission} of a text exercise, as exposed on the single text-exercise detail endpoint.
 * Carries the example submission with its (example) results so the management UI can render and manage example submissions.
 *
 * @param id              the example submission id
 * @param usedForTutorial whether this example submission is used for the tutorial
 * @param submission      the example text submission (with its results)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExampleSubmissionDTO(Long id, boolean usedForTutorial, TextSubmissionResponseDTO submission) implements Serializable {

    /**
     * Converts an {@link ExampleSubmission} into an {@link ExampleSubmissionDTO}.
     *
     * @param exampleSubmission the example submission to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ExampleSubmissionDTO of(ExampleSubmission exampleSubmission) {
        if (exampleSubmission == null) {
            return null;
        }
        TextSubmissionResponseDTO submission = exampleSubmission.getSubmission() instanceof TextSubmission textSubmission ? TextSubmissionResponseDTO.of(textSubmission) : null;
        return new ExampleSubmissionDTO(exampleSubmission.getId(), exampleSubmission.isUsedForTutorial(), submission);
    }
}
