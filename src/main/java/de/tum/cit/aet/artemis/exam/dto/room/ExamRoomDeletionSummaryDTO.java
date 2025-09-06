package de.tum.cit.aet.artemis.exam.dto.room;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomDeletionSummaryDTO(
    @NotNull String deleteDuration,
    int numberOfDeletedExamRooms
) {
}
// @formatter:on
