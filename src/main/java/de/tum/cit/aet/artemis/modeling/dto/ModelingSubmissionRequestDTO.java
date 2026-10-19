package de.tum.cit.aet.artemis.modeling.dto;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_SUBMISSION_MODEL_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_SUBMISSION_TEXT_LENGTH;

import java.io.Serializable;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Input DTO for creating or updating a modeling submission.
 * <p>
 * Carries only the values the client may set; participation and results are never accepted from the request body (the
 * participation is resolved server-side from the exercise and the current user, results are re-derived from persisted
 * state). The {@link Size} constraints are preserved from {@link de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission}
 * so oversized payloads keep returning HTTP 400.
 * <p>
 * Bare {@code @JsonInclude} (no explicit value, i.e. Jackson's ALWAYS default) is used rather than {@code NON_EMPTY}: request DTOs must
 * round-trip empty/blank values unchanged so the client can send them verbatim.
 *
 * @param id              the submission id ({@code null} on create)
 * @param model           the UML model JSON
 * @param explanationText the (optional) explanation text
 * @param submitted       whether the student submitted (vs. saved)
 */
@JsonInclude
public record ModelingSubmissionRequestDTO(Long id, @Size(max = MAX_SUBMISSION_MODEL_LENGTH, message = "The modeling submission is too large.") String model,
        @Size(max = MAX_SUBMISSION_TEXT_LENGTH, message = "The explanation of the modeling submission is too large.") String explanationText, Boolean submitted)
        implements Serializable {
}
