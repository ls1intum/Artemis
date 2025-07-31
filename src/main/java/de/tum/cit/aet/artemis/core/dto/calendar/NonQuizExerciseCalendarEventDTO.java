package de.tum.cit.aet.artemis.core.dto.calendar;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.util.CalendarEventRelatedEntity;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * A DTO primarily used to retrieve data about {@link ModelingExercise}s, {@link ProgrammingExercise}s, {@link FileUploadExercise}s and {@link TextExercise}s that are needed to
 * create {@link CalendarEventDTO}s.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NonQuizExerciseCalendarEventDTO(@NotNull CalendarEventRelatedEntity type, @NotNull String title, @Nullable ZonedDateTime releaseDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate) {
}
