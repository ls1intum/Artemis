package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionStatus;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionType;

/**
 * The progress of an asynchronous deletion of an exercise, an exam or an exercise group, sent over a websocket and kept for clients that connect later.
 * Serializable because the latest status is shared between server nodes.
 *
 * @param entityType              the kind of entity that is deleted
 * @param entityId                the id of the entity that is deleted
 * @param status                  whether the deletion is running, completed or failed
 * @param currentStep             stable key of the current step, which the client translates (for example {@code participations}); absent once the deletion ended
 * @param stepsCompleted          the number of steps that are completely done
 * @param totalSteps              the number of steps of this deletion
 * @param itemsProcessed          the items of the current step that are done, for example the exercises of an exam that are deleted
 * @param totalItems              the items of the current step
 * @param currentItemTitle        the title of the exercise that is currently deleted as part of an exam or an exercise group
 * @param weightedProgressPercent the progress between 0 and 100, weighted by the expected effort of the steps
 * @param startedAt               when the deletion started
 * @param updatedAt               when the server last reported progress, a running deletion whose update is older than a few minutes is stuck
 * @param errorMessage            why the deletion failed, a single short line
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EntityDeletionProgressDTO(EntityDeletionType entityType, long entityId, EntityDeletionStatus status, String currentStep, int stepsCompleted, int totalSteps,
        int itemsProcessed, int totalItems, String currentItemTitle, double weightedProgressPercent, ZonedDateTime startedAt, ZonedDateTime updatedAt, String errorMessage)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
