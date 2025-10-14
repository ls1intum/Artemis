package de.tum.cit.aet.artemis.lecture.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NebulaTranscriptionInitResponseDTO(@JsonProperty("transcriptionId") @NotNull String jobId, @NotNull String status) {
}
