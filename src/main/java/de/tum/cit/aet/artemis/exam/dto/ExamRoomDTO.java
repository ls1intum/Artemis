package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomDTO(
    String longRoomNumber,
    String shortRoomNumber,
    String name,
    String alternative_name,
    String building,
    Integer capacity
) {
}
// @formatter:on
