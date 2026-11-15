package de.tum.cit.aet.artemis.videosource.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

@Entity
@Table(name = "gocast_course_binding")
public class GocastCourseBinding extends DomainObject {

    @Column(name = "course_id", nullable = false, unique = true)
    @Parent
    private long courseId;

    @Column(name = "gocast_course_id", unique = true)
    private Long gocastCourseId;

    @Column(name = "gocast_integration_id", nullable = false)
    private long integrationId;

    @Column(name = "gocast_grant_id")
    private Long gocastGrantId;

    @Column(name = "course_slug")
    private String courseSlug;

    @Column(name = "course_name")
    private String courseName;

    @Column(name = "visibility", length = 32)
    private String visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private GocastBindingStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "state_hash", unique = true, length = 64)
    private String stateHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public Long getGocastCourseId() {
        return gocastCourseId;
    }

    public void setGocastCourseId(long gocastCourseId) {
        this.gocastCourseId = gocastCourseId;
    }

    public long getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(long integrationId) {
        this.integrationId = integrationId;
    }

    public Long getGocastGrantId() {
        return gocastGrantId;
    }

    public void setGocastGrantId(long gocastGrantId) {
        this.gocastGrantId = gocastGrantId;
    }

    public String getCourseSlug() {
        return courseSlug;
    }

    public void setCourseSlug(String courseSlug) {
        this.courseSlug = courseSlug;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public GocastBindingStatus getStatus() {
        return status;
    }

    public void setStatus(GocastBindingStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public String getStateHash() {
        return stateHash;
    }

    public void setStateHash(String stateHash) {
        this.stateHash = stateHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
