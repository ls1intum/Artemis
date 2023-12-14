package de.tum.in.www1.artemis.service.dto.iris.settings;

public record IrisCombinedSettingsDTO(IrisCombinedChatSubSettingsDTO irisChatSettings, IrisCombinedHestiaSubSettingsDTO irisHestiaSettings,
        IrisCombinedCodeEditorSubSettingsDTO irisCodeEditorSettings) {
}
