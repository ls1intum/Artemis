package de.tum.cit.aet.artemis.lecture.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NebulaTranscriptionRequestDTO(@NotNull String videoUrl, @NotNull Long lectureUnitId, @NotNull Long lectureId, @NotNull Long courseId, String courseName,
        String lectureName, String lectureUnitName, NebulaTranscriptionSettingsDTO settings) {

    /**
     * Settings for the transcription callback.
     *
     * @param authenticationToken The token used for callback authentication (also serves as runId)
     * @param artemisBaseUrl      The base URL of Artemis for callbacks
     */
    public record NebulaTranscriptionSettingsDTO(String authenticationToken, String artemisBaseUrl) {
    }
}
