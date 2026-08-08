package de.tum.cit.aet.artemis.globalsearch.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A durable record of a pending write to the shared {@code SearchableEntities} Weaviate collection.
 * <p>
 * Every metadata change routes through this outbox instead of writing to Weaviate directly. The
 * enqueue is a plain repository save that naturally joins any ambient transaction, so the intent is recorded
 * durably even if Weaviate is down or the node dies. A single-writer dispatcher on the scheduling node claims
 * rows in id (enqueue) order with {@code FOR UPDATE SKIP LOCKED}, performs the Weaviate write, and on success
 * deletes the row. On failure it increments {@link #attempts} and pushes {@link #nextAttemptAt} out with
 * exponential backoff, keeping the row for a later retry.
 * <p>
 * {@link WeaviateOutboxOperation#UPSERT} and {@link WeaviateOutboxOperation#DELETE_ENTITY} rows carry
 * {@link #entityType} and {@link #entityId}; UPSERT additionally carries a {@link #payload} snapshot (the
 * property map the call site already built, serialized as canonical JSON). The bulk {@code DELETE_*} variants
 * carry their parameters as JSON in {@link #params}.
 */
@Entity
@Table(name = "weaviate_outbox")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class WeaviateOutboxEntry extends DomainObject {

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 64)
    private WeaviateOutboxOperation operation;

    /**
     * The {@code SearchableEntitySchema.TypeValues} discriminator (UPSERT and DELETE_ENTITY only).
     */
    @Column(name = "entity_type", length = 64)
    private String entityType;

    /**
     * The database id of the underlying entity (UPSERT and DELETE_ENTITY only).
     */
    @Column(name = "entity_id")
    private Long entityId;

    /**
     * The serialized Weaviate property map for an UPSERT (canonical JSON), {@code null} for delete operations.
     */
    @Column(name = "payload")
    private String payload;

    /**
     * The serialized parameters for a bulk delete operation (JSON, e.g. {@code {"courseId":42}}),
     * {@code null} for UPSERT and DELETE_ENTITY.
     */
    @Column(name = "params")
    private String params;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private ZonedDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    public WeaviateOutboxEntry() {
        // Default constructor for JPA
    }

    /**
     * Builds an UPSERT entry for a single entity, immediately eligible for dispatch.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     * @param payload    the serialized property map (canonical JSON)
     * @return the outbox entry, ready to save
     */
    public static WeaviateOutboxEntry forUpsert(String entityType, Long entityId, String payload) {
        WeaviateOutboxEntry entry = new WeaviateOutboxEntry();
        entry.operation = WeaviateOutboxOperation.UPSERT;
        entry.entityType = entityType;
        entry.entityId = entityId;
        entry.payload = payload;
        entry.initTimestamps();
        return entry;
    }

    /**
     * Builds a DELETE_ENTITY entry for a single entity, immediately eligible for dispatch.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     * @return the outbox entry, ready to save
     */
    public static WeaviateOutboxEntry forDeleteEntity(String entityType, long entityId) {
        WeaviateOutboxEntry entry = new WeaviateOutboxEntry();
        entry.operation = WeaviateOutboxOperation.DELETE_ENTITY;
        entry.entityType = entityType;
        entry.entityId = entityId;
        entry.initTimestamps();
        return entry;
    }

    /**
     * Builds a bulk delete entry, immediately eligible for dispatch.
     *
     * @param operation the bulk delete variant
     * @param params    the serialized parameters (JSON)
     * @return the outbox entry, ready to save
     */
    public static WeaviateOutboxEntry forBulkDelete(WeaviateOutboxOperation operation, String params) {
        WeaviateOutboxEntry entry = new WeaviateOutboxEntry();
        entry.operation = operation;
        entry.params = params;
        entry.initTimestamps();
        return entry;
    }

    private void initTimestamps() {
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    public WeaviateOutboxOperation getOperation() {
        return operation;
    }

    public void setOperation(WeaviateOutboxOperation operation) {
        this.operation = operation;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public ZonedDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(ZonedDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Records a failed dispatch attempt: increments the attempt counter and schedules the next attempt.
     *
     * @param nextAttempt the time this entry becomes eligible for another dispatch
     */
    public void recordFailedAttempt(ZonedDateTime nextAttempt) {
        this.attempts++;
        this.nextAttemptAt = nextAttempt;
    }

    @Override
    public String toString() {
        return "WeaviateOutboxEntry{" + "id=" + getId() + ", operation=" + operation + ", entityType='" + entityType + '\'' + ", entityId=" + entityId + ", attempts=" + attempts
                + ", nextAttemptAt=" + nextAttemptAt + '}';
    }
}
