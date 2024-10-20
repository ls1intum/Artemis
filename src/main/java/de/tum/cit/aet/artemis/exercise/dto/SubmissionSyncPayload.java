package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionSyncPayload(Submission submission, User sender) {

}
