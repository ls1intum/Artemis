package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamRoomDTO(
    @NotNull String roomNumber,
    @NotNull String name,
    @NotNull String building,
    @NotNull Integer numberOfSeats,
    @NotNull Set<ExamRoomLayoutStrategyDTO> layoutStrategies
) {
}
// @formatter:on
