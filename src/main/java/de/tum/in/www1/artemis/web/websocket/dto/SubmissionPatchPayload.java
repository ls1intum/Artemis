package de.tum.in.www1.artemis.web.websocket.dto;

import de.tum.in.www1.artemis.domain.SubmissionPatch;
import de.tum.in.www1.artemis.domain.User;

public record SubmissionPatchPayload(SubmissionPatch submissionPatch, User sender) {
}
