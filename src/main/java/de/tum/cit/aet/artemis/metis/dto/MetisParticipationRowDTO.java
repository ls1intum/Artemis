package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;

/**
 * One comprehension check of an exercise together with its participation and the student's login and name, read in one statement for the whole dashboard. A
 * projection of {@code MetisCheckRepository.findDashboardRows}; no endpoint returns it, the dashboard maps it to {@link MetisCheckDTO}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisParticipationRowDTO(long participationId, long studentId, String studentLogin, @Nullable String firstName, @Nullable String lastName, long checkId,
        MetisCheckStatus status, @Nullable String commitHash, @Nullable String llmModel, @Nullable String promptVersion, @Nullable String failureReason,
        @Nullable ZonedDateTime generatedAt, @Nullable ZonedDateTime startedAt, @Nullable ZonedDateTime completedAt) {
}
