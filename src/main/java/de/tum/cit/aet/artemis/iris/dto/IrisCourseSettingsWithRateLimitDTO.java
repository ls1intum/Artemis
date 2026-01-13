package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCourseSettingsWithRateLimitDTO(Long courseId, IrisCourseSettings settings, IrisRateLimitConfiguration effectiveRateLimit,
        IrisRateLimitConfiguration applicationRateLimitDefaults) {
}
