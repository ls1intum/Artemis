package de.tum.cit.aet.artemis.hyperion.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * What the quiz question pool knows about one lecture unit of a course: the fingerprint of the unit's material at the last build,
 * whether an instructor excluded the unit from generation, and when it was last built. A row exists once a build has seen the
 * unit or an instructor has excluded it.
 */
@Entity
@Table(name = "quiz_question_pool_lecture_unit")
public class QuizQuestionPoolLectureUnit extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @Parent
    private Course course;

    @Column(name = "lecture_unit_id", nullable = false)
    private Long lectureUnitId;

    /** Fingerprint of the material the unit's questions were generated from; {@code null} until the first build. */
    @Column(name = "fingerprint")
    @Nullable
    private String fingerprint;

    @Column(name = "excluded", nullable = false)
    private boolean excluded;

    @Column(name = "last_built_at")
    @Nullable
    private ZonedDateTime lastBuiltAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Long getLectureUnitId() {
        return lectureUnitId;
    }

    public void setLectureUnitId(Long lectureUnitId) {
        this.lectureUnitId = lectureUnitId;
    }

    @Nullable
    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(@Nullable String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    @Nullable
    public ZonedDateTime getLastBuiltAt() {
        return lastBuiltAt;
    }

    public void setLastBuiltAt(@Nullable ZonedDateTime lastBuiltAt) {
        this.lastBuiltAt = lastBuiltAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
