package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedSettingsDTO(IrisCombinedChatSubSettingsDTO irisChatSettings, IrisCombinedLectureIngestionSubSettingsDTO irisLectureIngestionSettings,
        IrisCombinedHestiaSubSettingsDTO irisHestiaSettings, IrisCombinedCompetencyGenerationSubSettingsDTO irisCompetencyGenerationSettings,
        IrisCombinedProactivitySubSettingsDTO irisProactivitySettings) {
}
