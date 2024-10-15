package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedSettingsDTO(
        IrisCombinedChatSubSettingsDTO irisChatSettings,
        IrisCombinedTextExerciseChatSubSettingsDTO irisTextExerciseChatSettings,
        IrisCombinedLectureIngestionSubSettingsDTO irisLectureIngestionSettings,
        IrisCombinedCompetencyGenerationSubSettingsDTO irisCompetencyGenerationSettings,
        IrisCombinedProactivitySubSettingsDTO irisProactivitySettings
) {}
// @formatter:on
