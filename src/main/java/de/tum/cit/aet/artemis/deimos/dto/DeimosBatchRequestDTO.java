package de.tum.cit.aet.artemis.deimos.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

public record DeimosBatchRequestDTO(@NotNull ZonedDateTime from, @NotNull ZonedDateTime to) {
}
