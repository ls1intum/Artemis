package de.tum.cit.aet.artemis.exam.dto.room;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomSeatCountDTO(
    Long examRoomId,
    Long seatCount
) {
}
// @formatter:on
