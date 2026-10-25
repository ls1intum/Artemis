package de.tum.cit.aet.artemis.course.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/** A successful course Home render, independent of communication read state. */
@Entity
@Table(name = "course_home_visit", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "course_id" }))
public class CourseHomeVisit extends DomainObject {

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "course_id", nullable = false)
    private long courseId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "variant_cursor", nullable = false)
    private long variantCursor;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public long getVariantCursor() {
        return variantCursor;
    }

    public void setVariantCursor(long variantCursor) {
        this.variantCursor = variantCursor;
    }
}
