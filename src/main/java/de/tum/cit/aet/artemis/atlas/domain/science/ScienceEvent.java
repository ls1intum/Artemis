package de.tum.cit.aet.artemis.atlas.domain.science;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AggregateRoot;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A single recorded interaction, or a single recorded consent decision, for research purposes.
 * <p>
 * Kept for a course only while that course collects science data and the student has agreed to it; the consent
 * decisions themselves are stored as events of their own, so that an export can tell a collection window apart from a
 * period of no activity.
 */
@Entity
@Table(name = "science_event")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@AggregateRoot("Analytics event. `resource_id` is an untyped pointer with no foreign key, and `course_id` is optional because events recorded before course-level collection existed have no course to name, which is why the course cannot be its parent. Deleting the course does delete the events, including the consent markers: an event outliving its course would be unreachable from both the export and the per-course deletion. The row is keyed by login rather than by a user foreign key, so that an export keeps its shape once the account is anonymized.")
public class ScienceEvent extends DomainObject {

    @Column(name = "identity", nullable = false)
    private String identity;

    @Column(name = "timestamp", nullable = false)
    private ZonedDateTime timestamp;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "event_type", nullable = false)
    private ScienceEventType type;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "course_id")
    private Long courseId;

    /**
     * Creates an event that is not stored yet.
     *
     * @param identity   the login of the user the event belongs to
     * @param type       what happened
     * @param resourceId the resource it happened on, or {@code null} for a consent decision or a deletion
     * @param courseId   the course it belongs to
     * @param timestamp  when it happened
     * @return the new event
     */
    public static ScienceEvent of(String identity, ScienceEventType type, @Nullable Long resourceId, @Nullable Long courseId, ZonedDateTime timestamp) {
        ScienceEvent event = new ScienceEvent();
        event.setIdentity(identity);
        event.setType(type);
        event.setResourceId(resourceId);
        event.setCourseId(courseId);
        event.setTimestamp(timestamp);
        return event;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public ScienceEventType getType() {
        return type;
    }

    public void setType(ScienceEventType type) {
        this.type = type;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
}
