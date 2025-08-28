package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamRoomAdminOverviewDTO(
    @NotNull Integer numberOfStoredExamRooms,
    @NotNull Integer numberOfStoredExamSeats,
    @NotNull Integer numberOfStoredLayoutStrategies,
    @NotNull Set<ExamRoomDTO> newestUniqueExamRooms
) {
}
// @formatter:on
