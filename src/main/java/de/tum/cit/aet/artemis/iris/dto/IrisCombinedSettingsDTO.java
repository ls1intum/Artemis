package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedSettingsDTO(
        IrisCombinedProgrammingExerciseChatSubSettingsDTO irisProgrammingExerciseChatSettings,
        IrisCombinedTextExerciseChatSubSettingsDTO irisTextExerciseChatSettings,
        IrisCombinedCourseChatSubSettingsDTO irisCourseChatSettings,
        IrisCombinedLectureIngestionSubSettingsDTO irisLectureIngestionSettings,
        IrisCombinedCompetencyGenerationSubSettingsDTO irisCompetencyGenerationSettings,
        IrisCombinedLectureChatSubSettingsDTO irisLectureChatSettings,
        IrisCombinedFaqIngestionSubSettingsDTO irisFaqIngestionSettings,
        IrisCombinedTutorSuggestionSubSettingsDTO irisTutorSuggestionSettings
) {}
// @formatter:on
