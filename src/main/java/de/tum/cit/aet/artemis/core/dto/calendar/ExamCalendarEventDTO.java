package de.tum.cit.aet.artemis.core.dto.calendar;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * A DTO primarily used to retrieve data about an {@link Exam} that are needed to create {@link CalendarEventDTO}s.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamCalendarEventDTO(long originEntityId, @NotNull String title, @NotNull ZonedDateTime visibleDate, @NotNull ZonedDateTime startDate, @NotNull ZonedDateTime endDate,
        @Nullable ZonedDateTime publishResultsDate, @Nullable ZonedDateTime studentReviewStart, @Nullable ZonedDateTime studentReviewEnd, @Nullable String examiner) {
}
