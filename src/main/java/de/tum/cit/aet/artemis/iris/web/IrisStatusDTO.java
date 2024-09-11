package de.tum.cit.aet.artemis.iris.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisStatusDTO(boolean active, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo) {
}
