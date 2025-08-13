package de.tum.cit.aet.artemis.exam.dto.room;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.exam.domain.room.SeatCondition;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSeatDTO(@JsonProperty @NotNull String name, // 'rowName, seatName' or just 'seatName' if the row has no name

        @JsonProperty("condition") @NotNull SeatCondition seatCondition,

        @JsonProperty("x") @NotNull float xCoordinate,  // >= 0,

        @JsonProperty("y") @NotNull float yCoordinate  // >= 0
) {
}
