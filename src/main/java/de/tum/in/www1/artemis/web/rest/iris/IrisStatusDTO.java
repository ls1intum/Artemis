package de.tum.in.www1.artemis.web.rest.iris;

import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;

public record IrisStatusDTO(boolean active, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo) {
}
