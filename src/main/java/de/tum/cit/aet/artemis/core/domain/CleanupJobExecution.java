package de.tum.cit.aet.artemis.core.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "cleanup_job_execution")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CleanupJobExecution extends DomainObject {

    @Column(name = "delete_from")
    private ZonedDateTime deleteFrom;

    @Column(name = "delete_to")
    private ZonedDateTime deleteTo;

    @Column(name = "deleted_at")
    @NotNull
    private ZonedDateTime deletionTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type")
    private CleanupJobType cleanupJobType;

    public CleanupJobExecution() {
    }

    public ZonedDateTime getDeleteFrom() {
        return deleteFrom;
    }

    public void setDeleteFrom(ZonedDateTime deleteFrom) {
        this.deleteFrom = deleteFrom;
    }

    public ZonedDateTime getDeleteTo() {
        return deleteTo;
    }

    public void setDeleteTo(ZonedDateTime deleteTo) {
        this.deleteTo = deleteTo;
    }

    public ZonedDateTime getDeletionTimestamp() {
        return deletionTimestamp;
    }

    public void setDeletionTimestamp(ZonedDateTime deletionTimestamp) {
        this.deletionTimestamp = deletionTimestamp;
    }

    public CleanupJobType getCleanupJobType() {
        return cleanupJobType;
    }

    public void setCleanupJobType(CleanupJobType cleanupJobType) {
        this.cleanupJobType = cleanupJobType;
    }
}
