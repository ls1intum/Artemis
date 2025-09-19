package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomDTO(long id, @NotNull String roomNumber, @NotNull String name, @NotNull String building, int numberOfSeats,
        @NotNull Set<ExamRoomLayoutStrategyDTO> layoutStrategies) {
}
