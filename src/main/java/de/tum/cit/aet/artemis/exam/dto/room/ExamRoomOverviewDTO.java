package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Only sent from server to client
 *
 * @param newestUniqueExamRooms can never be {@code null} on the server side, but can be empty, thus can be {@code null}
 *                                  when received from the client because of the {@code JsonInclude.Include.NON_EMPTY}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomOverviewDTO(@NotNull Set<ExamRoomDTO> newestUniqueExamRooms) {
}
