package de.tum.cit.aet.artemis.exam.dto.room;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.exam.domain.room.SeatCondition;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSeatDTO(@JsonProperty String name, // 'rowName, seatName' or just 'seatName' if the row has no name

        @JsonProperty("condition") SeatCondition seatCondition,

        @JsonProperty("x") float xCoordinate,  // >= 0,

        @JsonProperty("y") float yCoordinate  // >= 0
) {
}
