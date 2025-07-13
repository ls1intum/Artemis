package de.tum.cit.aet.artemis.core.dao;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

public record ExamCalendarEventDAO(@NotNull String title, @NotNull ZonedDateTime visibleDate, @NotNull ZonedDateTime startDate, @NotNull ZonedDateTime endDate,
        ZonedDateTime publishResultsDate, ZonedDateTime studentReviewStart, ZonedDateTime studentReviewEnd, String examiner) {
}
