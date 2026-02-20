package de.tum.cit.aet.artemis.tutorialgroup.util;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RawTutorialGroupDetailSessionDTO(@NotNull ZonedDateTime start, @NotNull ZonedDateTime end, @NotNull String location, @NotNull TutorialGroupSessionStatus status,
        @Nullable Integer attendanceCount) {
}
