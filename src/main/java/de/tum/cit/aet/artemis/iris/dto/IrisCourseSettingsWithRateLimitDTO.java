package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.CourseIrisSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsDTO;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCourseSettingsWithRateLimitDTO(Long courseId, IrisCourseSettingsDTO settings, IrisRateLimitConfiguration effectiveRateLimit,
        IrisRateLimitConfiguration applicationRateLimitDefaults) {

    public static IrisCourseSettingsWithRateLimitDTO fromEntity(CourseIrisSettings entity, IrisRateLimitConfiguration effectiveRateLimit,
            IrisRateLimitConfiguration applicationRateLimitDefaults) {
        return new IrisCourseSettingsWithRateLimitDTO(entity.getCourseId(), entity.getSettings(), effectiveRateLimit, applicationRateLimitDefaults);
    }
}
