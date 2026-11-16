package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.metis.domain.MetisGroupWindow;

/**
 * The window in which the students of one tutorial group may take the comprehension check of an exercise, overriding the window of the exercise's
 * configuration.
 * <p>
 * The windows of an exercise are replaced as a whole and keyed by the tutorial group: in a request, {@code id} and {@code exerciseId} are ignored, the path of
 * the request decides the exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisGroupWindowDTO(@Nullable Long id, @Nullable Long exerciseId, @NotNull Long tutorialGroupId, @NotNull ZonedDateTime windowStart,
        @NotNull ZonedDateTime windowEnd) {

    /**
     * Maps a stored window.
     *
     * @param window the window row
     * @return the window with every column of the row
     */
    public static MetisGroupWindowDTO of(MetisGroupWindow window) {
        return new MetisGroupWindowDTO(window.getId(), window.getExerciseId(), window.getTutorialGroupId(), window.getWindowStart(), window.getWindowEnd());
    }
}
