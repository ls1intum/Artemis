package de.tum.cit.aet.artemis.iris.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventSessionType;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventSettings;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedEventSettingsDTO(boolean enabled, String selectedEventVariant, @Nullable IrisEventSessionType sessionType) {

    public static IrisCombinedEventSettingsDTO of(IrisEventSettings eventSettings) {
        return new IrisCombinedEventSettingsDTO(eventSettings.isEnabled(), eventSettings.getSelectedEventVariant(), eventSettings.getSessionType());
    }
}
