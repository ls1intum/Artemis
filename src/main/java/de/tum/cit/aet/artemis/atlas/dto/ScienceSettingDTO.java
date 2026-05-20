package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceSetting;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScienceSettingDTO(String settingId, boolean active) {

    @Nullable
    public static ScienceSettingDTO of(@Nullable ScienceSetting setting) {
        if (setting == null) {
            return null;
        }
        return new ScienceSettingDTO(setting.getSettingId(), setting.isActive());
    }
}
