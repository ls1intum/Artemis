package de.tum.cit.aet.artemis.videosource.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Persistent binding between an Artemis {@link de.tum.cit.aet.artemis.course.domain.Course} and a gocast (TUM Live) course.
 * <p>
 * Design decision — cross-module reference:
 * The entity stores {@code course_id} as a plain {@code Long} column rather than a JPA {@code @OneToOne}
 * to {@link de.tum.cit.aet.artemis.course.domain.Course}. This avoids a JPA-managed bidirectional
 * relationship across module boundaries (which would require a back-reference on {@code Course} and could
 * cause ArchUnit module-access violations). Referential integrity is enforced at the database level via the
 * named foreign key {@code fk_gocast_course_binding_course} defined in the Liquibase changelog, and the
 * application service layer resolves the Course object when needed.
 */
@Entity
@Table(name = "gocast_course_binding", uniqueConstraints = { @UniqueConstraint(name = "uc_gocast_course_binding_course_id", columnNames = { "course_id" }) })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GocastCourseBinding extends DomainObject {

    /**
     * FK to {@code course(id)} — enforced at DB level; stored as a plain {@code Long} to avoid
     * a cross-module JPA relationship.
     */
    @Column(name = "course_id", nullable = false, unique = true)
    private Long courseId;

    /** The numeric course identifier used by gocast (TUM Live). */
    @Column(name = "gocast_course_id", nullable = false)
    private Long gocastCourseId;

    /** The URL slug of the gocast course (e.g. {@code "eidi"}) — used for watch-page links. */
    @Column(name = "gocast_course_slug", nullable = false)
    private String gocastCourseSlug;

    /** Lifecycle status of this binding. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GocastBindingStatus status;

    /** Timestamp set automatically when the row is first inserted. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Timestamp updated automatically on every save. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getGocastCourseId() {
        return gocastCourseId;
    }

    public void setGocastCourseId(Long gocastCourseId) {
        this.gocastCourseId = gocastCourseId;
    }

    public String getGocastCourseSlug() {
        return gocastCourseSlug;
    }

    public void setGocastCourseSlug(String gocastCourseSlug) {
        this.gocastCourseSlug = gocastCourseSlug;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
