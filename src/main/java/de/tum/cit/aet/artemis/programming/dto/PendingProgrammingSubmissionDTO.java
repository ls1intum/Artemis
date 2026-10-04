package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The latest pending submission of a single student participation of a programming exercise. There is one entry per
 * student participation of the exercise; {@code submission} is {@code null} if the participation currently has no
 * pending submission (i.e. its latest submission already has a result). The client relies on receiving an entry for
 * every participation to prime its per-participation build-state cache and websocket subscriptions.
 *
 * @param participationId the id of the student participation
 * @param submission      the latest pending submission, or {@code null} if there is none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PendingProgrammingSubmissionDTO(long participationId, @Nullable ProgrammingSubmissionInfoDTO submission) implements Serializable {
}
