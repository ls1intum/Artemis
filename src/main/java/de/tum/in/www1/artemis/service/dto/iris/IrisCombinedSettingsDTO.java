package de.tum.in.www1.artemis.service.dto.iris;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedSettingsDTO(IrisCombinedChatSubSettingsDTO irisChatSettings, IrisCombinedHestiaSubSettingsDTO irisHestiaSettings,
        IrisCombinedCompetencyGenerationSubSettingsDTO irisCompetencyGenerationSettings) {
}
