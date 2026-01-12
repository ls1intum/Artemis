package de.tum.cit.aet.artemis.exam.dto.room;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/* Purely received from the client */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReseatInformationDTO(@NotNull Long examUserId, @NotNull String newRoom, String newSeat) {
}
