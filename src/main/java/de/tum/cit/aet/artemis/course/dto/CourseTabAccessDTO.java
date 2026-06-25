package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lightweight per-tab access flags for the course overview, used by the client {@code CourseOverviewGuard} to decide
 * whether a user may open a course tab without loading the full (expensive) course dashboard data.
 * <p>
 * Each flag is computed from a cheap indexed {@code exists}/{@code count} query or a course column. The EXERCISES tab is
 * always accessible, so it has no flag. The DASHBOARD AI-opt-out fallback is decided on the client from the user's LLM
 * selection, so only {@code dashboardEnabled} and {@code irisEnabled} are needed here.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseTabAccessDTO(boolean lecturesEnabled, boolean examsVisible, boolean competenciesOrPrerequisites, boolean tutorialGroups, boolean dashboardEnabled,
        boolean irisEnabled, boolean faqAccepted, boolean learningPathsEnabled, boolean communicationEnabled, boolean trainingEnabled) {
}
