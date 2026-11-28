package de.tum.cit.aet.artemis.videosource.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastApprovalStartDTO(String approvalUrl, Instant expiresAt) {

    @Override
    public String toString() {
        return "GocastApprovalStartDTO[approvalUrl=[REDACTED], expiresAt=" + expiresAt + "]";
    }
}
