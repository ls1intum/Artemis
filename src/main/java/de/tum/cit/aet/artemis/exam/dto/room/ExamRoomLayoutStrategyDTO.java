package de.tum.cit.aet.artemis.exam.dto.room;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategyType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRoomLayoutStrategyDTO(@NotNull String name, @NotNull LayoutStrategyType type, int capacity) {
}
