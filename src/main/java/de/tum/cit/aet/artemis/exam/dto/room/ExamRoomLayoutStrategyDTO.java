package de.tum.cit.aet.artemis.exam.dto.room;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategyType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
// @formatter:off
public record ExamRoomLayoutStrategyDTO(
    String name,
    LayoutStrategyType type,
    Integer capacity
) {
}
// @formatter:on
