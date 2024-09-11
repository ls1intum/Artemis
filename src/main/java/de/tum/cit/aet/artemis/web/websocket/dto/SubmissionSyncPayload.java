package de.tum.cit.aet.artemis.web.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionSyncPayload(Submission submission, User sender) {

}
