package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "iris_lecture_unit_sync_state")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisLectureUnitSyncState extends DomainObject {

    public static final String STATUS_CLEAN = "CLEAN";

    public static final String STATUS_DIRTY = "DIRTY";

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    @Column(name = "lecture_unit_id", nullable = false, unique = true)
    private Long lectureUnitId;

    @Column(name = "metadata_hash", length = 64)
    private String metadataHash;

    @Column(name = "last_synced_metadata_hash", length = 64)
    private String lastSyncedMetadataHash;

    @Column(name = "visibility_hash", length = 64)
    private String visibilityHash;

    @Column(name = "last_synced_visibility_hash", length = 64)
    private String lastSyncedVisibilityHash;

    @Column(name = "status", nullable = false, length = 30)
    private String status = STATUS_CLEAN;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private ZonedDateTime nextRetryAt;

    @Column(name = "last_error_key", length = 255)
    private String lastErrorKey;

    public Long getLectureUnitId() {
        return lectureUnitId;
    }

    public void setLectureUnitId(Long lectureUnitId) {
        this.lectureUnitId = lectureUnitId;
    }

    public String getMetadataHash() {
        return metadataHash;
    }

    public void setMetadataHash(String metadataHash) {
        this.metadataHash = metadataHash;
    }

    public String getLastSyncedMetadataHash() {
        return lastSyncedMetadataHash;
    }

    public void setLastSyncedMetadataHash(String lastSyncedMetadataHash) {
        this.lastSyncedMetadataHash = lastSyncedMetadataHash;
    }

    public String getVisibilityHash() {
        return visibilityHash;
    }

    public void setVisibilityHash(String visibilityHash) {
        this.visibilityHash = visibilityHash;
    }

    public String getLastSyncedVisibilityHash() {
        return lastSyncedVisibilityHash;
    }

    public void setLastSyncedVisibilityHash(String lastSyncedVisibilityHash) {
        this.lastSyncedVisibilityHash = lastSyncedVisibilityHash;
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
