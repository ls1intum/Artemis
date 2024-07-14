package de.tum.in.www1.artemis.service.iris.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.settings.event.IrisEventLevel;
import de.tum.in.www1.artemis.domain.iris.settings.event.IrisEventSettings;
import de.tum.in.www1.artemis.domain.iris.settings.event.IrisJolEventSettings;
import de.tum.in.www1.artemis.domain.iris.settings.event.IrisSubmissionFailedEventSettings;
import de.tum.in.www1.artemis.domain.iris.settings.event.IrisSubmissionSuccessfulEventSettings;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedEventSettingsDTO(boolean isActive, @Nullable ZonedDateTime deferredUntil, String pipelineVariant, Integer priority, @Nullable IrisEventLevel level,
        @Nullable Integer numberOfFailedAttempts, @Nullable Double successThreshold) {

    public static IrisCombinedEventSettingsDTO of(IrisEventSettings eventSettings) {
        return new IrisCombinedEventSettingsDTO(eventSettings.isActive(), eventSettings.getDeferredUntil(), eventSettings.getPipelineVariant(), eventSettings.getPriority(),
                eventSettings.getLevel(), null, null);
    }

    public static IrisCombinedEventSettingsDTO of(IrisSubmissionFailedEventSettings eventSettings) {
        return new IrisCombinedEventSettingsDTO(eventSettings.isActive(), eventSettings.getDeferredUntil(), eventSettings.getPipelineVariant(), eventSettings.getPriority(),
                eventSettings.getLevel(), eventSettings.getNumberOfFailedAttempts(), eventSettings.getSuccessThreshold());
    }

    public static IrisCombinedEventSettingsDTO of(IrisSubmissionSuccessfulEventSettings eventSettings) {
        return new IrisCombinedEventSettingsDTO(eventSettings.isActive(), eventSettings.getDeferredUntil(), eventSettings.getPipelineVariant(), eventSettings.getPriority(),
                eventSettings.getLevel(), null, eventSettings.getSuccessThreshold());
    }

    public static IrisCombinedEventSettingsDTO of(IrisJolEventSettings eventSettings) {
        return new IrisCombinedEventSettingsDTO(eventSettings.isActive(), eventSettings.getDeferredUntil(), eventSettings.getPipelineVariant(), eventSettings.getPriority(),
                eventSettings.getLevel(), null, null);
    }
}
