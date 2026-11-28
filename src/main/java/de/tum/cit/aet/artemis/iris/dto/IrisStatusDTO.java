package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Iris status visible to a course member.
 *
 * @param active        whether Pyris is available
 * @param rateLimitInfo the current user's quota
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisStatusDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) IrisRateLimitInformationDTO rateLimitInfo) {
}
