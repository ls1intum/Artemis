package de.tum.cit.aet.artemis.core.dao;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import javax.annotation.Nullable;

public record ExamCalendarEventDAO(@NotNull String title, @NotNull ZonedDateTime visibleDate, @NotNull ZonedDateTime startDate, @NotNull ZonedDateTime endDate,
        @Nullable ZonedDateTime publishResultsDate, @Nullable ZonedDateTime studentReviewStart, @Nullable ZonedDateTime studentReviewEnd, @Nullable String examiner) {
}
