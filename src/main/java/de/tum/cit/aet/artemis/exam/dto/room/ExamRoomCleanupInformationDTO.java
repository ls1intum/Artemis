package de.tum.cit.aet.artemis.exam.dto.room;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomCleanupInformationDTO(
    Integer numberOfOutdatedExamRooms,
    Integer numberOfRemovedExamRooms,
    Integer numberOfOutdatedExamSeats,
    Integer numberOfRemovedExamSeats,
    Integer numberOfOutdatedLayoutStrategies,
    Integer numberOfRemovedLayoutStrategies
) {
}
// @formatter:on
