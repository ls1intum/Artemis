package de.tum.cit.aet.artemis.core.dao;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.core.util.CalendarEventRelatedEntity;

public record NonQuizExerciseCalendarEventDAO(@NotNull CalendarEventRelatedEntity type, @NotNull String title, @Nullable ZonedDateTime releaseDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate) {
}
