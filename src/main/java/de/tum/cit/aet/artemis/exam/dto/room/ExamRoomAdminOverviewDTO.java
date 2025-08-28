package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomAdminOverviewDTO(
    @NotNull Integer numberOfStoredExamRooms,
    @NotNull Integer numberOfStoredExamSeats,
    @NotNull Integer numberOfStoredLayoutStrategies,
    @Nullable Set<ExamRoomDTO> newestUniqueExamRooms
) {
}
// @formatter:on
