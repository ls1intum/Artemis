package de.tum.cit.aet.artemis.exam.dto.room;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomDeletionSummaryDTO(int numberOfDeletedExamRooms) {
}
