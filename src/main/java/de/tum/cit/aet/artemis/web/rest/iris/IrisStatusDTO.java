package de.tum.cit.aet.artemis.web.rest.iris;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.service.iris.IrisRateLimitService;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisStatusDTO(boolean active, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo) {
}
