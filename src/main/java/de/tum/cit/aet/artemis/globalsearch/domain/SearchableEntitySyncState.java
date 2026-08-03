package de.tum.cit.aet.artemis.globalsearch.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Synchronization state for one {@code SearchableEntities} row, keyed by {@code (entity_type, entity_id)}.
 * <p>
 * Mirrors {@code IrisLectureUnitSyncState} (the merged metadata/visibility sync machine) but for the whole
 * SearchableEntities path, with two deliberate differences: it is keyed by the polymorphic
 * {@code (type, entity_id)} pair rather than a single foreign key, and it carries a single content-hash
 * dimension ({@link #contentHash} vs {@link #lastSyncedContentHash}) rather than separate metadata and
 * visibility hashes. There is no foreign key because SearchableEntities span nine entity types across many
 * tables, so no single owning table exists to reference.
 * <p>
 * Observe-only for now: the census reads and writes this state, but no production write path uses it as the
 * synchronization authority yet (that is the Ingestion Durable Sync project).
 */
@Entity
@Table(name = "searchable_entity_sync_state", uniqueConstraints = @UniqueConstraint(name = "uk_searchable_entity_sync_state_type_entity", columnNames = { "entity_type",
        "entity_id" }))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SearchableEntitySyncState extends DomainObject {

    public static final String STATUS_CLEAN = "CLEAN";

    public static final String STATUS_DIRTY = "DIRTY";

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "last_synced_content_hash", length = 64)
    private String lastSyncedContentHash;

    @Column(name = "status", nullable = false, length = 30)
    private String status = STATUS_CLEAN;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private ZonedDateTime nextRetryAt;

    @Column(name = "last_error_key", length = 255)
    private String lastErrorKey;

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

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getLastSyncedContentHash() {
        return lastSyncedContentHash;
    }

    public void setLastSyncedContentHash(String lastSyncedContentHash) {
        this.lastSyncedContentHash = lastSyncedContentHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public ZonedDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(ZonedDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastErrorKey() {
        return lastErrorKey;
    }

    public void setLastErrorKey(String lastErrorKey) {
        this.lastErrorKey = lastErrorKey;
    }
}
