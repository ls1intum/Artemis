package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomAdminOverviewDTO(
    Integer numberOfStoredExamRooms,
    Integer numberOfStoredExamSeats,
    Integer numberOfStoredLayoutStrategies,
    Set<ExamRoomDTO> newestUniqueExamRooms
) {
}
// @formatter:on
