package de.tum.cit.aet.artemis.exam.dto.room;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.exam.domain.room.SeatCondition;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSeatDTO(@NotNull String name, // 'rowName, seatName' or just 'seatName' if the row has no name
        @JsonProperty("condition") @NotNull SeatCondition seatCondition, @JsonProperty("x") @PositiveOrZero double xCoordinate,
        @JsonProperty("y") @PositiveOrZero double yCoordinate) {
}
