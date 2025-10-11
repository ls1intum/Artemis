package de.tum.cit.aet.artemis.versioning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.versioning.dto.ExerciseSnapshotDTO;

@Entity
@Table(name = "exercise_version")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ExerciseVersion extends AbstractAuditingEntity {

    // Direct ID access (for saving)
    @Column(name = "exercise_id", updatable = false, nullable = false)
    private Long exerciseId;

    @Column(name = "author_id", updatable = false, nullable = false)
    private Long authorId;

    // Entity references (for queries, read-only)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", insertable = false, updatable = false)
    private Exercise exercise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", insertable = false, updatable = false)
    private User author;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exercise_snapshot", columnDefinition = "json")
    private ExerciseSnapshotDTO exerciseSnapshotDTO;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exerciseId = exercise.getId();
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.authorId = author.getId();
    }

    public ExerciseSnapshotDTO getExerciseSnapshot() {
        return exerciseSnapshotDTO;
    }

    public void setExerciseSnapshot(ExerciseSnapshotDTO exerciseSnapshotDTO) {
        this.exerciseSnapshotDTO = exerciseSnapshotDTO;
    }

}
