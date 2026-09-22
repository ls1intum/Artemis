package de.tum.cit.aet.artemis.globalsearch.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.domain.AggregateRoot;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Append-only record of one thing the ingestion and searchable-entity pipelines did.
 * <p>
 * Rows are never updated, only inserted and eventually pruned by age. This is deliberately the only
 * append-only table in this area: {@link SearchableEntitySyncState} keeps one row per entity,
 * {@link WeaviateOutboxEntry} deletes its row on success, and the reconcile passes keep cycle counters
 * that reset — so none of them can answer "what happened while I was not looking". This table can.
 * <p>
 * It is an observability record, not a source of truth: nothing reads it back to make a decision, so a
 * lost or pruned row costs visibility and nothing else. Writes are therefore best-effort and must never
 * fail the pipeline work they describe.
 */
@Entity
@Table(name = "ingestion_event_log", indexes = { @Index(name = "idx_ingestion_event_log_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_ingestion_event_log_course_id", columnList = "course_id") })
@AggregateRoot("Observability event log; rows describe entities across many source tables and are owned by none of them.")
public class IngestionEventLogEntry extends DomainObject {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private IngestionEventKind kind;

    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;

    /**
     * The kind of thing this event is about — a searchable entity type such as {@code Exercise}, or
     * {@code LectureUnit} for ingestion runs. Free text rather than an enum because the searchable entity
     * types are registered dynamically.
     */
    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /**
     * The course the entity belongs to, when known. Null for events whose entity is not course-scoped or
     * whose course could not be resolved without an extra query the write path is not worth paying for.
     */
    @Nullable
    @Column(name = "course_id")
    private Long courseId;

    /**
     * Human-readable context for this event: the stage that failed, the quality score, the worker boot id.
     * Rendered verbatim in the admin feed, so it is written for a reader rather than for a parser.
     */
    @Nullable
    @Column(name = "detail", length = 512)
    private String detail;

    public IngestionEventLogEntry() {
        // Required by JPA.
    }

    public IngestionEventLogEntry(IngestionEventKind kind, ZonedDateTime occurredAt, String entityType, Long entityId, @Nullable Long courseId, @Nullable String detail) {
        this.kind = kind;
        this.occurredAt = occurredAt;
        this.entityType = entityType;
        this.entityId = entityId;
        this.courseId = courseId;
        this.detail = detail;
    }

    public IngestionEventKind getKind() {
        return kind;
    }

    public void setKind(IngestionEventKind kind) {
        this.kind = kind;
    }

    public ZonedDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(ZonedDateTime occurredAt) {
        this.occurredAt = occurredAt;
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

    @Nullable
    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(@Nullable Long courseId) {
        this.courseId = courseId;
    }

    @Nullable
    public String getDetail() {
        return detail;
    }

    public void setDetail(@Nullable String detail) {
        this.detail = detail;
    }
}
