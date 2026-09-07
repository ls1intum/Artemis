package de.tum.cit.aet.artemis.exam.dto.conduction;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Modeling-submission-specific content carried in the conduction payload (unwrapped into the submission object).
 * During a fresh conduction both fields are absent; on resume they carry the student's previously entered model and
 * explanation.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingSubmissionFieldsForConductionDTO(String model, String explanationText) {
}
