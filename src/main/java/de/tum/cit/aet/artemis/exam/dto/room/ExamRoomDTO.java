package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomDTO(
    String roomNumber,
    String name,
    String building,
    Integer numberOfSeats,
    Set<ExamRoomLayoutStrategyDTO> layoutStrategies
) {
}
// @formatter:on
