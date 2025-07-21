package de.tum.cit.aet.artemis.exam.dto.room;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomUniqueRoomsDTO(
    Integer numberOfUniqueRooms,
    Integer numberOfUniqueSeats,
    Integer numberOfUniqueLayoutStrategies
) {
    public ExamRoomUniqueRoomsDTO(Number numberOfUniqueRooms, Number numberOfUniqueSeats, Number numberOfUniqueLayoutStrategies) {
        this(
            numberOfUniqueRooms.intValue(),
            numberOfUniqueSeats.intValue(),
            numberOfUniqueLayoutStrategies.intValue()
        );
    }
}
// @formatter:on
