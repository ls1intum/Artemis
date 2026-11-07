package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ExternalSubmissionIneligibility;

/**
 * A student suggested in the external submission dialog.
 *
 * @param login         the login the dialog posts when the student is selected
 * @param name          the full name shown in the suggestion
 * @param ineligibility why the student cannot receive an external result, absent if they can
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExternalSubmissionCandidateDTO(String login, String name, ExternalSubmissionIneligibility ineligibility) {
}
