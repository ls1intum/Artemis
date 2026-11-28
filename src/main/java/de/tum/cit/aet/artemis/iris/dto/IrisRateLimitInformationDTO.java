package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService.IrisRateLimitInformation;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The current user's Iris quota as returned by the status endpoint.
 *
 * @param currentMessageCount     the number of messages in the current window
 * @param rateLimit               the maximum count, or -1 for unlimited
 * @param rateLimitTimeframeHours the window in hours, or -1 for unlimited
 * @param rateLimitExceeded       whether the quota currently blocks another message
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisRateLimitInformationDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int currentMessageCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int rateLimit, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int rateLimitTimeframeHours,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean rateLimitExceeded) {

    /**
     * @param information the quota calculated by the rate limit service
     * @return the equivalent REST payload
     */
    public static IrisRateLimitInformationDTO of(IrisRateLimitInformation information) {
        return new IrisRateLimitInformationDTO(information.currentMessageCount(), information.rateLimit(), information.rateLimitTimeframeHours(),
                information.isRateLimitExceeded());
    }
}
