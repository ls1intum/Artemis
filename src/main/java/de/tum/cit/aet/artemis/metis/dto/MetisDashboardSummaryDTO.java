package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The tiles above the results dashboard of an exercise, computed from its rows: how many students took part and how far they got, how many checks still wait for
 * a review, the mean score of the main questions of completed checks (null without any score), the number of follow-up questions, and the latest end of any
 * window of the exercise (null when no window has an end).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisDashboardSummaryDTO(int participants, int completed, int inProgress, int notStarted, int skippedOrFailed, int awaitingReview, @Nullable Double averageScore,
        int followUps, @Nullable ZonedDateTime windowEnd) {
}
