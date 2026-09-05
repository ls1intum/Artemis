package de.tum.cit.aet.artemis.videosource.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "gocast_course_binding")
public class GocastCourseBinding extends DomainObject {

    @Column(name = "course_id", nullable = false, unique = true)
    private long courseId;

    @Column(name = "gocast_course_id", nullable = false, unique = true)
    private long gocastCourseId;

    @Column(name = "gocast_grant_id", nullable = false, updatable = false)
    private long gocastGrantId;

    @Column(name = "course_slug", nullable = false)
    private String courseSlug;

    @Column(name = "course_name", nullable = false)
    private String courseName;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private GocastBindingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public long getGocastCourseId() {
        return gocastCourseId;
    }

    public void setGocastCourseId(long gocastCourseId) {
        this.gocastCourseId = gocastCourseId;
    }

    public long getGocastGrantId() {
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
