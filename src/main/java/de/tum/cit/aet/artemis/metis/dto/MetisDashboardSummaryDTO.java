package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The tiles above the results dashboard of an exercise, computed from its rows: how many students took part and how far they got, how many checks still wait for
 * a review, the mean score of the main questions of completed checks (null without any score), the number of follow-up questions, and the latest end of any
 * window of the exercise (null when no window has an end).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisDashboardSummaryDTO(@NotNull int participants, @NotNull int completed, @NotNull int inProgress, @NotNull int notStarted, @NotNull int skippedOrFailed,
        @NotNull int awaitingReview, @Nullable Double averageScore, @NotNull int followUps, @Nullable ZonedDateTime windowEnd) {
}
