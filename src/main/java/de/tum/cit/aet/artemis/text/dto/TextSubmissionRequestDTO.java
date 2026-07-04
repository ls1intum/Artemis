package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Language;

/**
 * Input DTO for creating or updating a text submission.
 * <p>
 * Carries only the values the client may set; participation, results and text blocks are never accepted from the request.
 * The {@link Size} constraint is preserved from {@code TextSubmission#text} so oversized payloads keep returning HTTP 400.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextSubmissionRequestDTO(Long id, @Size(max = Constants.MAX_SUBMISSION_TEXT_LENGTH, message = "The text submission is too large.") String text, Language language,
        Boolean submitted) implements Serializable {
}
