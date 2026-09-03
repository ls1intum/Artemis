package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.UserStoryEffort;

/**
 * The effort a participant reports for a user story exercise, in hours. Used in both directions: the student reads their
 * own pair and writes it back.
 * <p>
 * Either value may be {@code null} - a student may record the estimate and come back for the actual effort later.
 *
 * @param estimatedEffort hours the participant expected the story to take
 * @param actualEffort    hours the story actually took
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserStoryEffortDTO(@Nullable Double estimatedEffort, @Nullable Double actualEffort) {

    public static UserStoryEffortDTO of(@Nullable UserStoryEffort effort) {
        return effort == null ? new UserStoryEffortDTO(null, null) : new UserStoryEffortDTO(effort.getEstimatedEffort(), effort.getActualEffort());
    }
}
