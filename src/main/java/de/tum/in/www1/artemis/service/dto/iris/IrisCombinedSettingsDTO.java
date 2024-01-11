package de.tum.in.www1.artemis.service.dto.iris;

public record IrisCombinedSettingsDTO(IrisCombinedChatSubSettingsDTO irisChatSettings, IrisCombinedHestiaSubSettingsDTO irisHestiaSettings,
        IrisCombinedCodeEditorSubSettingsDTO irisCodeEditorSettings, IrisCombinedCompetencyGenerationSubSettingsDTO irisCompetencyGenerationSettings) {
}
