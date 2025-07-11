package de.tum.cit.aet.artemis.core.dao;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import javax.annotation.Nullable;

import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventRelatedEntity;

public record NonQuizExerciseCalendarEventDAO(@NotNull CalendarEventRelatedEntity type, @NotNull String title, @Nullable ZonedDateTime releaseDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate) {
}
