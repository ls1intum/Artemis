package de.tum.cit.aet.artemis.videosource.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "gocast_approval_attempt")
public class GocastApprovalAttempt extends DomainObject {

    @Column(name = "course_id", nullable = false, unique = true)
    private long courseId;

    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    private String stateHash;

    @Column(name = "gocast_integration_id", nullable = false)
    private long integrationId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public String getStateHash() {
        return stateHash;
    }

    public void setStateHash(String stateHash) {
        this.stateHash = stateHash;
    }

    public long getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(long integrationId) {
        this.integrationId = integrationId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

}
