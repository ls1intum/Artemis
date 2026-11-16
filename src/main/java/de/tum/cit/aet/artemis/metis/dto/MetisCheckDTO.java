package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;

/**
 * The comprehension check of one participation as the dashboard shows it: its lifecycle state, the snapshot of the generation (commit, model, prompt version)
 * and the times of generation, start and completion.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisCheckDTO(@NotNull long id, @NotNull long participationId, @NotNull MetisCheckStatus status, @Nullable String commitHash, @Nullable String llmModel,
        @Nullable String promptVersion, @Nullable String failureReason, @Nullable ZonedDateTime generatedAt, @Nullable ZonedDateTime startedAt,
        @Nullable ZonedDateTime completedAt) {

    /**
     * Maps the check columns of a dashboard row.
     *
     * @param row the projection of a check together with its participation and student
     * @return the check of that row
     */
    public static MetisCheckDTO of(MetisParticipationRowDTO row) {
        return new MetisCheckDTO(row.checkId(), row.participationId(), row.status(), row.commitHash(), row.llmModel(), row.promptVersion(), row.failureReason(), row.generatedAt(),
                row.startedAt(), row.completedAt());
    }
}
