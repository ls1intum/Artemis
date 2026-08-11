package de.tum.cit.aet.artemis.exam.dto.conduction;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Text-submission-specific content carried in the conduction payload (unwrapped into the submission object). During a
 * fresh conduction the {@code text} is absent; on resume it carries the student's previously entered answer.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextSubmissionFieldsForConductionDTO(String text) {
}
