package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.AggregateRoot;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A TUM.Live lecture Artemis uploaded and no longer uses, waiting to be deleted there. It is removed only after TUM.Live confirms the deletion, so the obligation
 * stays on record while TUM.Live is unreachable or refuses it.
 */
@Entity
@Table(name = "tum_live_recording_cleanup")
@AggregateRoot("An obligation towards TUM.Live that outlives the unit, lecture and course it came from.")
public class TumLiveRecordingCleanup extends DomainObject {

    /** The course the lecture was uploaded for: if the course was connected to TUM.Live again, its new grant can delete it. */
    @Column(name = "course_id", nullable = false)
    private long courseId;

    @Column(name = "integration_id", nullable = false)
    private long integrationId;

    @Column(name = "grant_id", nullable = false)
    private long grantId;

    @Column(name = "external_ref", nullable = false, length = 64)
    private String externalRef;

    @Column(name = "next_attempt_at", nullable = false)
    private ZonedDateTime nextAttemptAt;

    /** Why TUM.Live refused the deletion (the grant was revoked, or the reference belongs to another grant); retried daily. */
    @Column(name = "blocked_reason", length = 255)
    private String blockedReason;

    public long getCourseId() {
        return courseId;
    }

    public long getIntegrationId() {
        return integrationId;
    }

    public long getGrantId() {
        return grantId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public ZonedDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getBlockedReason() {
        return blockedReason;
    }
}
