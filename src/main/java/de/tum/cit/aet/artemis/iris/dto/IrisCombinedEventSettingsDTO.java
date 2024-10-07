package de.tum.in.www1.artemis.service.iris.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.settings.event.IrisEventSettings;
import de.tum.in.www1.artemis.domain.iris.settings.event.IrisEventTarget;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedEventSettingsDTO(boolean isActive, String pipelineVariant, @Nullable IrisEventTarget target) {

    public static IrisCombinedEventSettingsDTO of(IrisEventSettings eventSettings) {
        return new IrisCombinedEventSettingsDTO(eventSettings.isActive(), eventSettings.getPipelineVariant(), eventSettings.getTarget());
    }
}
