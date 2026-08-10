package de.tum.cit.aet.artemis.globalsearch.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Ledger of what has actually been confirmed written to the shared {@code SearchableEntities} Weaviate
 * collection, keyed uniquely by {@code (entity_type, entity_id)}.
 * <p>
 * The dispatcher refreshes the row on every successful upsert, storing the {@link #contentHash} of the
 * property map it wrote and the {@link #syncedAt} time. A later reconcile pass reads this ledger to decide which
 * entities are missing (no row = never indexed, including pre-feature courses) or drifted (hash mismatch)
 * and need to be re-enqueued.
 */
@Entity
@Table(name = "searchable_entity_sync_state", uniqueConstraints = @UniqueConstraint(name = "uc_searchable_entity_sync_state_type_entity", columnNames = { "entity_type",
        "entity_id" }))
public class SearchableEntitySyncState extends DomainObject {

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "synced_at", nullable = false)
    private ZonedDateTime syncedAt;

    public SearchableEntitySyncState() {
        // Default constructor for JPA
    }

    public SearchableEntitySyncState(String entityType, Long entityId, String contentHash, ZonedDateTime syncedAt) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.contentHash = contentHash;
        this.syncedAt = syncedAt;
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

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public ZonedDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(ZonedDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    @Override
    public String toString() {
        return "SearchableEntitySyncState{" + "id=" + getId() + ", entityType='" + entityType + '\'' + ", entityId=" + entityId + ", contentHash='" + contentHash + '\''
                + ", syncedAt=" + syncedAt + '}';
    }
}
