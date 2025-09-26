package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record LectureCreateDTO(@NotNull String title, @Nullable String channelName, @Nullable ZonedDateTime visibleDate, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate) {
}
