package de.tum.cit.aet.artemis.core.dao;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

public record LectureCalendarEventDAO(@NotNull String title, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
}
