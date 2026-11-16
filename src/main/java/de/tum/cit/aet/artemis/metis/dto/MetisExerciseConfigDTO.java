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
 * limits the annotations state are the bounds of {@link MetisExerciseConfig}, which the database checks as well, as far as a single field can check them; the
 * rules that relate two fields (the shortest time limit at most the longest one, the follow-up threshold below the highest rating, the window end after its
 * start) are checked by the service.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisExerciseConfigDTO(@NotNull Long exerciseId, @NotNull Boolean enabled, @Nullable ZonedDateTime windowStart, @Nullable ZonedDateTime windowEnd,
        @NotNull @Min(MetisExerciseConfig.MIN_MAIN_QUESTIONS) @Max(MetisExerciseConfig.MAX_MAIN_QUESTIONS) Integer mainQuestionCount,
        @NotNull @Min(0) @Max(MetisExerciseConfig.MAX_FOLLOW_UPS_PER_QUESTION) Integer maxFollowUpsPerQuestion,
        @NotNull @Min(MetisExerciseConfig.MIN_TIME_LIMIT_SECONDS) @Max(MetisExerciseConfig.MAX_TIME_LIMIT_SECONDS) Integer minSecondsPerQuestion,
        @NotNull @Min(MetisExerciseConfig.MIN_TIME_LIMIT_SECONDS) @Max(MetisExerciseConfig.MAX_TIME_LIMIT_SECONDS) Integer maxSecondsPerQuestion,
        @NotNull @Min(MetisExerciseConfig.MIN_SCALE_VALUE) @Max(MetisExerciseConfig.MAX_SCALE_VALUE) Integer answerLength,
        @NotNull @Min(MetisExerciseConfig.MIN_SCALE_VALUE) @Max(MetisExerciseConfig.MAX_SCALE_VALUE) Integer complexity,
        @NotNull @Min(MetisExerciseConfig.MIN_LIKERT_POINTS) @Max(MetisExerciseConfig.MAX_LIKERT_POINTS) Integer likertScalePoints,
        @NotNull @Min(1) @Max(MetisExerciseConfig.MAX_LIKERT_POINTS - 1) Integer followUpThreshold, @NotNull Boolean reviewRequired,
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
     * half-way, or that a node of the previous version stored during a rolling deployment, until a request that saves its settings or a tutorial-group window
     * adds the row, and for an exercise of another type, which never has one. Nothing is written.
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
