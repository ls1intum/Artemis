package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOperation;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;

/**
 * One row waiting in the Weaviate outbox.
 *
 * @param id            the row's id, which is also its dispatch order
 * @param operation     the write to perform
 * @param origin        what enqueued it: a live edit, or one of the reconcile passes
 * @param entityType    the entity type for single-entity operations, null for bulk ones
 * @param entityId      the entity id for single-entity operations, null for bulk ones
 * @param attempts      how many times this write has already failed
 * @param nextAttemptAt when it becomes eligible again
 * @param createdAt     when it was enqueued
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OutboxEntryDTO(long id, WeaviateOutboxOperation operation, WeaviateOutboxOrigin origin, @Nullable String entityType, @Nullable Long entityId, int attempts,
        ZonedDateTime nextAttemptAt, ZonedDateTime createdAt) {

    /**
     * Projects a stored outbox row onto the wire shape.
     *
     * @param entry the stored row
     * @return the DTO for the queue view
     */
    public static OutboxEntryDTO of(WeaviateOutboxEntry entry) {
        return new OutboxEntryDTO(entry.getId(), entry.getOperation(), entry.getOrigin(), entry.getEntityType(), entry.getEntityId(), entry.getAttempts(), entry.getNextAttemptAt(),
                entry.getCreatedAt());
    }
}
