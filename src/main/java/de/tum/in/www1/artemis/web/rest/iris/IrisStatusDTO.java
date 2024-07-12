package de.tum.in.www1.artemis.web.rest.iris;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisStatusDTO(boolean active, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo) {
}
