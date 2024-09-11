package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * Wrapper Class to send achieved points and achieved scores of a student to the client for courses / exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionWithComplaintDTO(Submission submission, Complaint complaint) {
}
