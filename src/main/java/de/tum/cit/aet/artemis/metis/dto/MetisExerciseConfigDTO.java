package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;

/**
 * The Metis configuration of one programming exercise, read by tutors and written by editors.
 * <p>
 * Every request field is boxed and {@code @NotNull}, so that a field missing from a request fails validation instead of silently becoming 0 or false. The
 * limits the annotations state are the ones a single field can check; the rules that relate two fields (the shortest time limit at most the longest one, the
 * follow-up threshold below the highest rating, the window end after its start) are checked by the service.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisExerciseConfigDTO(@NotNull Long exerciseId, @NotNull Boolean enabled, @Nullable ZonedDateTime windowStart, @Nullable ZonedDateTime windowEnd,
        @NotNull @Min(1) @Max(5) Integer mainQuestionCount, @NotNull @Min(0) @Max(3) Integer maxFollowUpsPerQuestion, @NotNull @Min(30) @Max(3600) Integer minSecondsPerQuestion,
        @NotNull @Min(30) @Max(3600) Integer maxSecondsPerQuestion, @NotNull @Min(1) @Max(5) Integer answerLength, @NotNull @Min(1) @Max(5) Integer complexity,
        @NotNull @Min(5) @Max(7) Integer likertScalePoints, @NotNull @Min(1) @Max(6) Integer followUpThreshold, @NotNull Boolean reviewRequired,
        @Nullable @Size(max = 2000) String instructorHint) {

    /**
     * Maps a stored configuration.
     *
     * @param config the configuration row of the exercise
     * @return the configuration with every column of the row
     */
    public static MetisExerciseConfigDTO of(MetisExerciseConfig config) {
        return new MetisExerciseConfigDTO(config.getExerciseId(), config.isEnabled(), config.getWindowStart(), config.getWindowEnd(), config.getMainQuestionCount(),
                config.getMaxFollowUpsPerQuestion(), config.getMinSecondsPerQuestion(), config.getMaxSecondsPerQuestion(), config.getAnswerLength(), config.getComplexity(),
                config.getLikertScalePoints(), config.getFollowUpThreshold(), config.isReviewRequired(), config.getInstructorHint());
    }

    /**
     * The settings every configuration row starts with: the field defaults of {@link MetisExerciseConfig}, switched off and without a window. Every programming
     * exercise owns its row from the moment it is stored, so these stand in only where there is no row: for a programming exercise whose creation failed
     * half-way, until a settings request or the next update of the exercise adds the row, and for an exercise of another type, which never has one. Nothing is
     * written.
     *
     * @param exerciseId the exercise without a configuration row
     * @return the default configuration of that exercise
     */
    public static MetisExerciseConfigDTO defaults(long exerciseId) {
        MetisExerciseConfig config = new MetisExerciseConfig();
        config.setExerciseId(exerciseId);
        return of(config);
    }
}
