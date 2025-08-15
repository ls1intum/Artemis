package de.tum.cit.aet.artemis.core.dto.calendar;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/**
 * A DTO primarily used to retrieve data about a {@link Lecture} that are needed to create {@link CalendarEventDTO}s.
 */
@JsonInclude(Include.NON_EMPTY)
public record LectureCalendarEventDTO(@NotNull String title, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
}
