package de.tum.cit.aet.artemis.tutorialgroup.util;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RawTutorialGroupDetailSessionDTO(@NotNull long id, @NotNull ZonedDateTime start, @NotNull ZonedDateTime end, @NotNull String location, boolean isCancelled,
        boolean isCancelledByFreePeriod, @Nullable Integer attendanceCount) {
}
