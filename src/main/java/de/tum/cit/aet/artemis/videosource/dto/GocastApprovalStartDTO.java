package de.tum.cit.aet.artemis.videosource.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastApprovalStartDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String approvalUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt) {

    @Override
    public String toString() {
        return "GocastApprovalStartDTO[approvalUrl=[REDACTED], expiresAt=" + expiresAt + "]";
    }
}
