package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomDTO(@NotNull String roomNumber, @NotNull String name, @NotNull String building, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int numberOfSeats,
        @NotNull Set<ExamRoomLayoutStrategyDTO> layoutStrategies) {
}
