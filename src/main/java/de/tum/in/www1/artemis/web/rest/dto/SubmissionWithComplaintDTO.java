package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.Submission;

/**
 * Wrapper Class to send achieved points and achieved scores of a student to the client for courses / exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionWithComplaintDTO(Submission submission, Complaint complaint) {
}
