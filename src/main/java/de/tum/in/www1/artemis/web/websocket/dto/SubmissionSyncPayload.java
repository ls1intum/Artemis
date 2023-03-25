package de.tum.in.www1.artemis.web.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionSyncPayload(Submission submission, User sender) {

}
