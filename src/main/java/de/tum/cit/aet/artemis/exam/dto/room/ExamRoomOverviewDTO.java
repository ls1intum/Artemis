package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomOverviewDTO(int numberOfStoredExamRooms, int numberOfStoredExamSeats, int numberOfStoredLayoutStrategies, @NotNull Set<ExamRoomDTO> newestUniqueExamRooms) {
}
