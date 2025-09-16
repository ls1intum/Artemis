package de.tum.cit.aet.artemis.tutorialgroup.util;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

public record TutorialGroupDetailSessionData(@NotNull ZonedDateTime start, @NotNull ZonedDateTime end, @NotNull String location, @NotNull TutorialGroupSessionStatus status,
        @Nullable Integer attendanceCount) {
}
