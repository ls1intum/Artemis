package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;

/**
 * One row of the Weaviate outbox, as shown by the ingestion dashboard's queue view.
 * <p>
 * There is deliberately no "processing" flag: the dispatcher never mutates a row while applying it, which is what
 * lets a crash mid-batch leave the row to be re-read and re-applied. A row is therefore either waiting to be
 * picked up or, once applied, gone. The dashboard infers completion from a row's disappearance.
 *
 * @param id            outbox id, which is also the dispatch order
 * @param operation     the write this row will perform
 * @param entityType    the searchable entity type, absent for bulk operations that have no single entity
 * @param entityId      the entity's id, absent for bulk operations
 * @param origin        which path enqueued the row: a live change or one of the reconcile passes
 * @param attempts      how many times the dispatcher has tried and failed; 0 for a row not yet attempted
 * @param nextAttemptAt when the row becomes eligible again, in the future only while backing off after a failure
 * @param createdAt     when the row was enqueued
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OutboxQueueEntryDTO(long id, String operation, String entityType, Long entityId, String origin, int attempts, ZonedDateTime nextAttemptAt, ZonedDateTime createdAt) {

    public static OutboxQueueEntryDTO of(WeaviateOutboxEntry entry) {
        return new OutboxQueueEntryDTO(entry.getId(), entry.getOperation().name(), entry.getEntityType(), entry.getEntityId(), entry.getOrigin().name(), entry.getAttempts(),
                entry.getNextAttemptAt(), entry.getCreatedAt());
    }
}
