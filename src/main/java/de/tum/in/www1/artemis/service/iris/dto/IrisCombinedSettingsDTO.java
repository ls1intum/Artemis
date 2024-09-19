package de.tum.in.www1.artemis.service.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedSettingsDTO(
        IrisCombinedChatSubSettingsDTO irisChatSettings,
        IrisCombinedTextExerciseChatSubSettingsDTO irisTextExerciseChatSettings,
        IrisCombinedLectureIngestionSubSettingsDTO irisLectureIngestionSettings,
        IrisCombinedCompetencyGenerationSubSettingsDTO irisCompetencyGenerationSettings
) {}
// @formatter:on
