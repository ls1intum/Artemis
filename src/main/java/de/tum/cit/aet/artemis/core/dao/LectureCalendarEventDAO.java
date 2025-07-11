package de.tum.cit.aet.artemis.core.dao;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import javax.annotation.Nullable;

public record LectureCalendarEventDAO(@NotNull String title, @Nullable ZonedDateTime visibleDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate) {
}
